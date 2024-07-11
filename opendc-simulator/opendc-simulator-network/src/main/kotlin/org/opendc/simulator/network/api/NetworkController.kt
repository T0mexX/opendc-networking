package org.opendc.simulator.network.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.getNodesById
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.slf4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.UUID



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
    }

    private val virtualMapping = mutableMapOf<Long, NodeId>()

    internal val instantSrc: NetworkInstantSrc = NetworkInstantSrc(instantSource)

    public val currentInstant: Instant
        get() = instantSrc.instant()

    private var lastUpdate: Instant = Instant.ofEpochMilli(ms.MIN_VALUE)

    init {
        instantSource?.let { lastUpdate = it.instant() }
        StaticECMP.eToEFlows = network.netFlowById
    }

    public val energyRecorder: NetworkEnergyRecorder =
        NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())

    public val internetNetworkInterface: NetNodeInterface =
        getNetInterfaceOf(network.internet.id)
            ?: throw IllegalStateException("network did not initialize the internet abstract node correctly")

    private val claimedHostIds = mutableSetOf<NodeId>()
    private val claimedCoreSwitchIds = mutableSetOf<NodeId>()
    internal val flowsById = mutableMapOf<FlowId, NetFlow>()


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

    public fun virtualMap(from: NodeId, to: NodeId) {
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

    public fun startFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId, // TODO: understand how multiple core switches work
        desiredDataRate: Kbps = .0,
        flowId: FlowId = IdDispenser.nextFlowId,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null,
        physicalTransmitterId: Boolean = false
    ): NetFlow? {
        val mappedTransmitterId: NodeId =  if (physicalTransmitterId) transmitterId else mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        val netFlow = NetFlow(
                transmitterId = mappedTransmitterId,
                destinationId = mappedDestId,
                id = flowId,
                desiredDataRate = desiredDataRate,
            )

        dataRateOnChangeHandler?.let {
            netFlow.withDataRateOnChangeHandler(dataRateOnChangeHandler)
        }

        return startFlow(netFlow)
    }

    private fun startFlow(netFlow: NetFlow): NetFlow? {
        if (netFlow.transmitterId !in network.endPointNodes)
            return log.errAndNull("unable to startInstant network flow from node ${netFlow.transmitterId}, " +
                "node does not exist or is not an end-point node")

        if (netFlow.destinationId !in network.endPointNodes)
            return log.errAndNull("unable to startInstant network flow directed to node ${netFlow.destinationId}, " +
                "node does not exist or it is not an end-point-node")

        if (netFlow.id in flowsById)
            return log.errAndNull("unable to startInstant network flow with nodeId ${netFlow.id}, " +
                "a flow with nodeId ${netFlow.id} already exists")

//        if (netFlow.transmitterId in claimedHostIds
//            || netFlow.transmitterId in claimedCoreSwitchIds)
//            log.warn("starting flow through controller interface from node whose interface was claimed, " +
//                "best practice would be to use either only the controller or the node interface for each node")

        flowsById[netFlow.id] = netFlow
        network.startFlow(netFlow)

        return netFlow
    }

    public fun startOrUpdateFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId,
        desiredDataRate: Kbps = .0,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null,
        physicalTransmitterId: Boolean = false
    ): NetFlow? {
        val mappedTransmitterId: NodeId =  if (physicalTransmitterId) transmitterId else mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        flowsById.values.find {
            it.transmitterId == mappedTransmitterId && it.destinationId == mappedDestId
        } ?. let {
            it.desiredDataRate = desiredDataRate
            if (dataRateOnChangeHandler != null) it.withDataRateOnChangeHandler(dataRateOnChangeHandler)
            return it
        } ?: return startFlow(
            transmitterId = mappedTransmitterId,
            destinationId = mappedDestId,
            desiredDataRate = desiredDataRate,
            dataRateOnChangeHandler = dataRateOnChangeHandler
        )
    }

    public fun stopFlow(flowId: FlowId): NetFlow? {
        return network.netFlowById[flowId]
            ?. let {
                when (network.stopFlow(flowId)) {
                    Result.SUCCESS -> it
                    else -> null
                }
            }
    }

    public fun getNetInterfaceOf(uuid: UUID): NetNodeInterface? =
        getNetInterfaceOf(uuid.node())

    public fun getNetInterfaceOf(nodeId: NodeId): NetNodeInterface? {
        if (nodeId !in network.endPointNodes)
            return log.errAndNull("unable to retrieve network interface, " +
                "node does not exist or does not provide an interface")

        return NetNodeInterfaceImpl(nodeId)
    }

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
        withVirtualMapping: Boolean = true
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


        netWorkload.execAll(controller = this)
    }

    private fun mappedOrSelf(id: NodeId): NodeId =
        virtualMapping[id] ?: let { id }




    private inner class NetNodeInterfaceImpl(override val nodeId: NodeId): NetNodeInterface {

        override fun startFlow(
            destinationId: NodeId?, // TODO: understand how multiple core switches work
            desiredDataRate: Kbps,
            dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
        ): NetFlow? =
            startFlow(
                transmitterId = this.nodeId,
                destinationId = destinationId ?: internetNetworkInterface.nodeId,
                dataRateOnChangeHandler = dataRateOnChangeHandler,
                physicalTransmitterId = true
            )

        override fun stopFlow(id: FlowId) {
            flowsById[id]?.let { flow ->
                if (flow.transmitterId != this.nodeId)
                    return log.error("unable to stop flow $id, node ${this.nodeId} has no control over it")

                stopFlow(id)
            } ?: return log.error("unable to stop flow $id, flow does not exists")
        }

        override fun getMyFlow(id: FlowId): NetFlow? {
            flowsById[id]?.let { flow ->
                if (flow.transmitterId != this.nodeId)
                    return log.errAndNull("unable to retrieve flow $id, node ${this.nodeId} has no control over it")
                return flow
            } ?: return  log.errAndNull("unable to retrieve flow $id,flow does not exists")
        }

        override fun fromInternet(
            desiredDataRate: Kbps,
            dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
        ): NetFlow {
            return internetNetworkInterface.startFlow(
                destinationId = this.nodeId,
                desiredDataRate = desiredDataRate,
                dataRateOnChangeHandler = dataRateOnChangeHandler,
            ) !!
        }
    }
}


private fun Logger.errAndNull(msg: String): Nothing? {
    this.error(msg)
    return null
}
