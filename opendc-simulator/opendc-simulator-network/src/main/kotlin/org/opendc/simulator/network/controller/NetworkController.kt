package org.opendc.simulator.network.controller

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.Cluster
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.energy.NetworkEnergyRecorder
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
public class NetworkController(file: File) {
    private companion object { private val log by logger() }

    private val network: Network

    public val energyRecorder: NetworkEnergyRecorder

    private val hostNodes = mutableMapOf<NodeId, Cluster>()
    private val flowsById = mutableMapOf<FlowId, NetFlow>()
    private val flowIdDispenser = IdDispenser()

    init {
        val jsonReader = Json() { ignoreUnknownKeys = true }
        val netSpec = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
        network = netSpec.buildFromSpecs()

        energyRecorder = NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())
    }

    public fun claimNextNode(): NodeId? =
        network.hostsById.keys
            .filterNot { it in hostNodes.keys }
            .firstOrNull()
            ?. let {
                hostNodes[it] = network.hostsById[it] !!
                it
        }

    public fun claimNode(uuid: UUID) { claimNode(uuid.node()) }

    public fun claimNode(nodeId: NodeId) {
        check (nodeId in hostNodes)
        { "unable to claim node id $nodeId, id already claimed" }

        network.hostsById[nodeId]?.let {
            hostNodes[nodeId] = it
        } ?: throw IllegalArgumentException(
            "unable to claim node id $nodeId, id not existent or not associated to a host node"
        )
    }

    public fun startFlow(
        transmitterId: NodeId,
        destinationId: NodeId = -1, // TODO: understand how multiple core switches work
        desiredDataRate: Kbps = .0,
        flowId: FlowId = flowIdDispenser.nextId,
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
        if (netFlow.transmitterId !in hostNodes)
            return log.errAndNull("unable to start network flow from node ${netFlow.transmitterId}, " +
                "node does not exist or is not an end-point node")

        if (netFlow.destinationId !in network.endPointNodes)
            return log.errAndNull("unable to start network flow directed to node ${netFlow.destinationId}, " +
                "node does not exist or it is not an end-point-node")

        if (netFlow.id in flowsById)
            return log.errAndNull("unable to start network flow with id ${netFlow.id}, " +
                "a flow with id ${netFlow.id} already exists")

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

    public fun advanceBy(duration: Duration) { advanceBy(duration.toMillis()) }

    public fun advanceBy(ms: ms) { network.advanceBy(ms) }
}


private fun Logger.errAndNull(msg: String): Nothing? {
    this.error(msg)
    return null
}
