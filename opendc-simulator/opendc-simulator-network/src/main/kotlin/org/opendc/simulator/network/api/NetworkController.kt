package org.opendc.simulator.network.api

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.slf4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.UUID
import kotlin.time.TimeSource


@OptIn(ExperimentalSerializationApi::class)
public class NetworkController internal constructor(
    private val network: Network,
    instantSource: InstantSource? = null,
) {
    public companion object {
        internal val log by logger()

        public fun fromFile(file: File, instantSource: InstantSource? = null): NetworkController {
            val jsonReader = Json() { ignoreUnknownKeys = true }
            val netSpec = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            return NetworkController(netSpec.buildFromSpecs(), instantSource)
        }

        internal var nano: Long = 0
    }

    private val virtualMapping = mutableMapOf<Long, NodeId>()

    internal val instantSrc: NetworkInstantSrc = NetworkInstantSrc(instantSource)

    public val currentInstant: Instant
        get() = instantSrc.instant()

    private var lastUpdate: Instant = Instant.ofEpochMilli(ms.MIN_VALUE)

    internal val flowsById: Map<FlowId, NetFlow>

    init {
        instantSource?.let { lastUpdate = it.instant() }
        StaticECMP.eToEFlows = network.flowsById
        flowsById = network.flowsById

        log.info(buildString {
            appendLine("\nNetwork Info:")
            appendLine("num of core switches: ${network.getNodesById<CoreSwitch>().size}")
            appendLine("num of host nodes: ${network.getNodesById<HostNode>().size}")
        })
    }

    public val energyRecorder: NetworkEnergyRecorder =
        NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())

    public val internetNetworkInterface: NetNodeInterface =
        getNetInterfaceOf(INTERNET_ID)
            ?: throw IllegalStateException("network did not initialize the internet abstract node correctly")

    private val claimedHostIds = mutableSetOf<NodeId>()
    private val claimedCoreSwitchIds = mutableSetOf<NodeId>()


    public fun claimNextHostNode(): NetNodeInterface? {
        val hostsById = network.getNodesById<HostNode>()
        return hostsById.keys
            .filterNot { it in claimedHostIds }
            .firstOrNull()
            ?.let {
                claimedHostIds.add(it)
                getNetInterfaceOf(it)
            } ?: log.errAndNull("unable to claim host node, none available")
    }

    public fun claimNextCoreNode(): NetNodeInterface? {
        val coreSwitches = network.getNodesById<CoreSwitch>()
        return coreSwitches
            .keys
            .filterNot { it in claimedHostIds }
            .firstOrNull()
            ?.let {
                claimedCoreSwitchIds.add(it)
                getNetInterfaceOf(it)
            } ?: log.errAndNull("unable to claim core switch node, none available")
    }

    internal fun virtualMap(from: NodeId, to: NodeId) {
        if (from in virtualMapping)
            log.warn("overriding mapping of virtual node id $from")

        if (from in network.endPointNodes)
            log.warn("shadowing physical node $from with virtual mapping, " +
                "it will not be possible to use the physical id directly")

        virtualMapping[from] = to
    }

    public fun claimNode(uuid: UUID): NetNodeInterface? =
        claimNode(uuid.node())

    public fun claimNode(nodeId: NodeId): NetNodeInterface? {
        check (nodeId !in claimedHostIds)
        { "unable to claim node nodeId $nodeId, nodeId already claimed" }

        (network.getNodesById<HostNode>() + network.getNodesById<CoreSwitch>())[nodeId]
            ?.let {
            claimedHostIds.add(nodeId)
        } ?: throw IllegalArgumentException(
            "unable to claim node nodeId $nodeId, nodeId not existent or not associated to a core switch or a host node"
        )

        return getNetInterfaceOf(nodeId)
    }

    public suspend fun startFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId, // TODO: understand how multiple core switches work
        desiredDataRate: Kbps = .0,
        flowId: FlowId = IdDispenser.nextFlowId,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null,
    ): NetFlow? {
        val mappedTransmitterId: NodeId =   mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        val netFlow = NetFlow(
                transmitterId = mappedTransmitterId,
                destinationId = mappedDestId,
                id = flowId,
                desiredDataRate = desiredDataRate,
            )

        dataRateOnChangeHandler?.let {
            netFlow.withThroughputOnChangeHandler(dataRateOnChangeHandler)
        }

        return startFlow(netFlow)
    }

    private suspend fun startFlow(netFlow: NetFlow): NetFlow? {
        if (netFlow.transmitterId !in network.endPointNodes)
            return log.errAndNull("unable to startInstant network flow from node ${netFlow.transmitterId}, " +
                "node does not exist or is not an end-point node")

        if (netFlow.destinationId !in network.endPointNodes)
            return log.errAndNull("unable to startInstant network flow directed to node ${netFlow.destinationId}, " +
                "node does not exist or it is not an end-point-node")

        if (netFlow.id in network.flowsById)
            return log.errAndNull("unable to startInstant network flow with nodeId ${netFlow.id}, " +
                "a flow with nodeId ${netFlow.id} already exists")

//        if (netFlow.transmitterId in claimedHostIds
//            || netFlow.transmitterId in claimedCoreSwitchIds)
//            log.warn("starting flow through controller interface from node whose interface was claimed, " +
//                "best practice would be to use either only the controller or the node interface for each node")

//        network.flowsById[netFlow.id] = netFlow
        network.startFlow(netFlow)

        return netFlow
    }


    public suspend fun startOrUpdateFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId,
        desiredDataRate: Kbps = .0,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null,
        physicalTransmitterId: Boolean = false
    ): NetFlow? {
        val mappedTransmitterId: NodeId =  if (physicalTransmitterId) transmitterId else mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        val tm = TimeSource.Monotonic.markNow()
        network.flowsById.values.find {
            it.transmitterId == mappedTransmitterId && it.destinationId == mappedDestId
        } ?. let {
            nano += tm.elapsedNow().inWholeNanoseconds
            it.setDesiredDataRate(desiredDataRate)

            if (dataRateOnChangeHandler != null) it.withThroughputOnChangeHandler(dataRateOnChangeHandler)
            return it
        } ?: let {
            nano += tm.elapsedNow().inWholeNanoseconds
            val f = startFlow(
                transmitterId = transmitterId,
                destinationId = destinationId,
                desiredDataRate = desiredDataRate,
                dataRateOnChangeHandler = dataRateOnChangeHandler
            )

            return f
        }
    }

    public suspend fun stopFlow(flowId: FlowId): NetFlow? =
        network.stopFlow(flowId)

    public fun getNetInterfaceOf(uuid: UUID): NetNodeInterface? =
        getNetInterfaceOf(uuid.node())

    public fun getNetInterfaceOf(nodeId: NodeId): NetNodeInterface? =
        network.endPointNodes[nodeId]?.let {
            NetNodeInterfaceImpl(it, this)
        } ?: log.errAndNull("unable to retrieve network interface, " +
                "node does not exist or does not provide an interface")

    public fun sync() {
        if (instantSrc.isExternalSource) {
            val timeSpan = instantSrc.millis() - lastUpdate.toEpochMilli()
            advanceBy(timeSpan, suppressWarn = true)
        } else log.error("unable to synchronize network, instant source not set. Use 'advanceBy()' instead")
    }

    public fun advanceBy(duration: Duration, suppressWarn: Boolean = false) {
        advanceBy(duration.toMillis(), suppressWarn)
    }

    public fun advanceBy(ms: ms, suppressWarn: Boolean = false) {
        if (instantSrc.isInternalSource)
            instantSrc.advanceTime(ms)
        else  if (!suppressWarn)
            log.warn("advancing time directly while instant source is set, this can cause ambiguity. " +
                "You can synchronize the network according to the instant source with 'sync()'")

        network.advanceBy(ms)
        energyRecorder.advanceBy(ms)
    }

    public fun execWorkload(
        netWorkload: SimNetWorkload,
        resetFlows: Boolean = true,
        resetTime: Boolean = true,
        resetEnergy: Boolean = true,
        resetClaimedNodes: Boolean = true,
        resetVirtualMapping: Boolean = true,
        withVirtualMapping: Boolean = true,
        withProgressBar: Boolean = true
    ) {
        if (instantSrc.isExternalSource)
            return log.error("unable to run a workload while controller has an external time source")

        if (resetFlows) network.resetFlows()

        if (resetTime) instantSrc.internal = netWorkload.startInstant.toEpochMilli()

        if (resetEnergy) energyRecorder.reset()

        if (resetClaimedNodes) {
            claimedHostIds.clear()
            claimedCoreSwitchIds.clear()
        }

        if (resetVirtualMapping) virtualMapping.clear()

        if (withVirtualMapping) netWorkload.performVirtualMappingOn(this)


        runBlocking {
            val netRunnerJob: Job = network.launch()

            if (withProgressBar) {
                val pb: ProgressBar = ProgressBarBuilder()
                    .setInitialMax(netWorkload.size.toLong())
                    .setStyle(ProgressBarStyle.ASCII)
                    .setTaskName("Simulating...").build()

                delay(1000)

                while (netWorkload.hasNext()) {
                    val deadline = netWorkload.peek().deadline
                    var steps: Long = 0

                    while (netWorkload.hasNext() && netWorkload.peek().deadline <= deadline) {
                        steps++
                        netWorkload.execNext(controller = this@NetworkController)
                    }

                    network.awaitStability()
                    pb.stepBy(steps)
//                    println(currentInstant.toEpochMilli())
//                    println(n.getFmtFlows())
                }
                pb.refresh()
            }

            netRunnerJob.cancelAndJoin()
        }
    }

    private fun mappedOrSelf(id: NodeId): NodeId =
        virtualMapping[id] ?: let { id }





}


private fun Logger.errAndNull(msg: String): Nothing? {
    this.error(msg)
    return null
}
