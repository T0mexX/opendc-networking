package org.opendc.simulator.network.interfaces

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
import org.slf4j.Logger
import java.io.File
import java.time.Duration
import java.util.UUID



@OptIn(ExperimentalSerializationApi::class)
public class NetworkController internal constructor(private val network: Network) {
    public companion object {
        private val log by logger()

        public fun fromFile(file: File): NetworkController {
            val jsonReader = Json() { ignoreUnknownKeys = true }
            val netSpec = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            return NetworkController(netSpec.buildFromSpecs())
        }
    }

    public val energyRecorder: NetworkEnergyRecorder =
        NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())

    public val internetNetworkInterface: NetNodeInterface =
        getNetInterfaceOf(network.internet.id)
            ?: throw IllegalStateException("network did not initialize the internet abstract node correctly")

    private val claimedHostsById = mutableMapOf<NodeId, HostNode>()
    private val claimedCoreNodesById = mutableMapOf<NodeId, CoreSwitch>()
    private val flowsById = mutableMapOf<FlowId, NetFlow>()


    public fun claimNextHostNode(): NetNodeInterface? {
        val hostsById = network.getNodesById<HostNode>()
        return hostsById.keys
            .filterNot { it in claimedHostsById.keys }
            .firstOrNull()
            ?.let {
                claimedHostsById[it] = hostsById[it]!!
                getNetInterfaceOf(it)
            }
    }

    public fun claimNextCoreNode(): NetNodeInterface? {
        val coreSwitches = network.getNodesById<CoreSwitch>()
        return coreSwitches
            .keys
            .filterNot { it in claimedHostsById.keys }
            .firstOrNull()
            ?.let {
                claimedCoreNodesById[it] = coreSwitches[it] !!
                getNetInterfaceOf(it)
            }
    }

    public fun claimNode(uuid: UUID): NetNodeInterface? =
        claimNode(uuid.node())

    public fun claimNode(nodeId: NodeId): NetNodeInterface? {
        check (nodeId in claimedHostsById)
        { "unable to claim node nodeId $nodeId, nodeId already claimed" }

        network.getNodesById<HostNode>()[nodeId]?.let {
            claimedHostsById[nodeId] = it
        } ?: throw IllegalArgumentException(
            "unable to claim node nodeId $nodeId, nodeId not existent or not associated to a host node"
        )

        return getNetInterfaceOf(nodeId)
    }

    public fun startFlow(
        transmitterId: NodeId,
        destinationId: NodeId = -1, // TODO: understand how multiple core switches work
        desiredDataRate: Kbps = .0,
        flowId: FlowId = IdDispenser.nextFlowId,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow? {
        val netFlow = NetFlow(
                transmitterId = transmitterId,
                destinationId = destinationId,
                id = flowId,
                desiredDataRate = desiredDataRate,
            )

        dataRateOnChangeHandler?.let {
            netFlow.withDataRateOnChangeHandler(dataRateOnChangeHandler)
        }

        return startFlow(netFlow)
    }

    public fun startFlow(netFlow: NetFlow): NetFlow? {
        if (netFlow.transmitterId !in claimedHostsById)
            return log.errAndNull("unable to start network flow from node ${netFlow.transmitterId}, " +
                "node does not exist or is not an end-point node")

        if (netFlow.destinationId !in network.endPointNodes)
            return log.errAndNull("unable to start network flow directed to node ${netFlow.destinationId}, " +
                "node does not exist or it is not an end-point-node")

        if (netFlow.id in flowsById)
            return log.errAndNull("unable to start network flow with nodeId ${netFlow.id}, " +
                "a flow with nodeId ${netFlow.id} already exists")

        if (netFlow.transmitterId in claimedHostsById.keys
            || netFlow.transmitterId in claimedCoreNodesById)
            log.warn("starting flow through controller interface from node whose interface was claimed, " +
                "best practice would be to use either only the controller or the node interface for each node")

        flowsById[netFlow.id] = netFlow
        network.startFlow(netFlow)

        return netFlow
    }

    public fun stopFlow(flowId: FlowId): Boolean {
        return when (network.stopFlow(flowId)) {
            Result.SUCCESS -> true
            else -> false
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

    public fun advanceBy(duration: Duration) { advanceBy(duration.toMillis()) }

    public fun advanceBy(ms: ms) {
        network.advanceBy(ms)
        energyRecorder.advanceBy(ms)
    }

    private inner class NetNodeInterfaceImpl(val nodeId: NodeId): NetNodeInterface {

        override fun startFlow(
            destinationId: NodeId, // TODO: understand how multiple core switches work
            desiredDataRate: Kbps,
            flowId: FlowId,
            dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
        ): NetFlow? {
            val netFlow = NetFlow(
                    transmitterId = this.nodeId,
                    destinationId = destinationId,
                    id = flowId,
                    desiredDataRate = desiredDataRate,
                )

            dataRateOnChangeHandler?.let {
                netFlow.withDataRateOnChangeHandler(dataRateOnChangeHandler)
            }

            return startFlow(netFlow)
        }

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
            flowId: FlowId,
            desiredDataRate: Kbps,
            dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
        ): NetFlow {
            TODO("Not yet implemented")
            // TODO: implement
        }
    }
}


private fun Logger.errAndNull(msg: String): Nothing? {
    this.error(msg)
    return null
}
