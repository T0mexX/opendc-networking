package org.opendc.simulator.network.api

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
import org.opendc.simulator.network.utils.errAndNull
import org.slf4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.UUID
import kotlin.system.measureNanoTime


@OptIn(ExperimentalSerializationApi::class)
public class NetworkController(
    internal val network: Network,
    instantSource: InstantSource? = null,
): AutoCloseable {
    public companion object {
        public val log: Logger by logger()

        public fun fromFile(file: File, instantSource: InstantSource? = null): NetworkController {
            val jsonReader = Json() { ignoreUnknownKeys = true }
            val netSpec = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            return NetworkController(netSpec.build(), instantSource)
        }

        public fun fromPath(path: String, instantSource: InstantSource? = null): NetworkController =
            fromFile(File(path))

        private var bo : Long = 0
        private var nano: Long = 0
    }

    private val virtualMapping = mutableMapOf<Long, NodeId>()

    internal var instantSrc: NetworkInstantSrc = NetworkInstantSrc(instantSource)
        private set

    public fun setInstantSource(instantSource: InstantSource) {
        instantSrc = NetworkInstantSrc(instantSource)
    }

    public val currentInstant: Instant
        get() = instantSrc.instant()

    private var lastUpdate: Instant = Instant.ofEpochMilli(ms.MIN_VALUE)

    init {
        instantSource?.let { lastUpdate = it.instant() }
        StaticECMP.eToEFlows = network.flowsById

        network.launch()
        log.info(buildString {
            appendLine("\nNetwork Info:")
            appendLine("num of core switches: ${network.getNodesById<CoreSwitch>().size}")
            appendLine("num of host nodes: ${network.getNodesById<HostNode>().size}")
            appendLine("num of nodes: ${network.nodes.size}")
        })
    }

    public val energyRecorder: NetworkEnergyRecorder =
        NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())

    public val internetNetworkInterface: NetNodeInterface =
        getNetInterfaceOf(INTERNET_ID)
            ?: throw IllegalStateException("network did not initialize the internet abstract node correctly")

    private val _claimedHostIds = mutableSetOf<NodeId>()
    internal val claimedHostIds: Set<NodeId> get() = _claimedHostIds

    private val claimedCoreSwitchIds = mutableSetOf<NodeId>()


    public fun claimNextHostNode(): NetNodeInterface? {
        val hostsById = network.getNodesById<HostNode>()
        return hostsById.keys
            .filterNot { it in _claimedHostIds }
            .firstOrNull()
            ?.let {
                _claimedHostIds.add(it)
                getNetInterfaceOf(it)
            } ?: log.errAndNull("unable to claim host node, none available")
    }

    public fun claimNextCoreNode(): NetNodeInterface? {
        val coreSwitches = network.getNodesById<CoreSwitch>()
        return coreSwitches
            .keys
            .filterNot { it in _claimedHostIds }
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
        // Check that node is not already claimed.
        if (nodeId in _claimedHostIds || nodeId in claimedCoreSwitchIds)
            return log.errAndNull("unable to claim node nodeId $nodeId, nodeId already claimed")

        // If node is host.
        network.getNodesById<HostNode>()[nodeId]
            ?.let {
                _claimedHostIds.add(nodeId)
                return getNetInterfaceOf(nodeId)
            }

        // If node is core switch.
        network.getNodesById<CoreSwitch>()[nodeId]
            ?.let {
                claimedCoreSwitchIds.add(nodeId)
                return getNetInterfaceOf(nodeId)
            }

        // else
        return log.errAndNull("unable to claim node nodeId $nodeId, " +
            "nodeId not existent or not associated to a core switch or a host node")
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
            return log.errAndNull("unable to start network flow from node ${netFlow.transmitterId}, " +
                "node does not exist or is not an end-point node")

        if (netFlow.destinationId !in network.endPointNodes)
            return log.errAndNull("unable to start network flow directed to node ${netFlow.destinationId}, " +
                "node does not exist or it is not an end-point-node")

        if (netFlow.id in network.flowsById)
            return log.errAndNull("unable to start network flow with flow id ${netFlow.id}, " +
                "a flow that id already exists")

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
        bo++
        val mappedTransmitterId: NodeId =  if (physicalTransmitterId) transmitterId else mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        return network.flowsById.values.find {
            it.transmitterId == mappedTransmitterId && it.destinationId == mappedDestId
        } ?. let {
            it.setDesiredDataRate(desiredDataRate)
            if (dataRateOnChangeHandler != null) it.withThroughputOnChangeHandler(dataRateOnChangeHandler)

            it
        } ?: startFlow(
                transmitterId = transmitterId,
                destinationId = destinationId,
                desiredDataRate = desiredDataRate,
                dataRateOnChangeHandler = dataRateOnChangeHandler
        )
    }

    public suspend fun stopFlow(flowId: FlowId): NetFlow? =
        network.stopFlow(flowId)

    private fun getNetInterfaceOf(nodeId: NodeId): NetNodeInterface? =
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
                "You can synchronize the network with the instant source with 'sync()'")

        network.advanceBy(ms)
        energyRecorder.advanceBy(ms)
    }

    public fun execWorkload(
        netWorkload: SimNetWorkload,
        reset: Boolean = true,
        withVirtualMapping: Boolean = true
    ) {
        if (netWorkload.hasNext().not()) return log.error("network workload empty")

        if (instantSrc.isExternalSource) {
            log.warn("network controller external time source will " +
                "be replaced with an internal one to tun the workload.")
            instantSrc = NetworkInstantSrc(internal = instantSrc.instant().toEpochMilli())
        }

        if (reset) {
            network.resetFlows()
//            network.launch() //TODO
            instantSrc.setInternalTime(netWorkload.peek().deadline)
            energyRecorder.reset()
            _claimedHostIds.clear()
            claimedCoreSwitchIds.clear()
            virtualMapping.clear()
        }

        if (withVirtualMapping) netWorkload.performVirtualMappingOn(this)

        runBlocking {
            val pb: ProgressBar = ProgressBarBuilder()
                .setInitialMax(netWorkload.size.toLong())
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Simulating...").build()

            delay(1000)

            with (netWorkload) {
                while (hasNext()) {
                    val nextDeadline = peek().deadline
                    pb.stepBy(execUntil(nextDeadline))
                    nano += measureNanoTime { network.awaitStability() }
                }
            }
            pb.refresh()
        }
        println("bo: $bo")
        println("nano: $nano")
    }

    private fun mappedOrSelf(id: NodeId): NodeId =
        virtualMapping[id] ?: let { id }

    override fun close() {
        network.runnerJob?.cancel()
    }
}

