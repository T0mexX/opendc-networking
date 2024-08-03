package org.opendc.simulator.network.components

import kotlinx.coroutines.yield
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.logger

/**
 * Interface representing a node in a [Network].
 */
internal interface Node: FlowView {
    // TODO: allow connection between nodes without immediate vector routing forwarding,
    //  to optimize network building

    /**
     * ID of the node. Uniquely identifies the node in the [Network].
     */
    val id: NodeId

    /**
     * Port speed in Kbps full duplex.
     */
    val portSpeed: DataRate

    /**
     * Ports of ***this*** [Node], full duplex.
     */
    val ports: List<Port>

    /**
     * Number of ports of ***this*** [Node].
     */
    val numOfPorts: Int get() { return ports.size }

    /**
     * Policy that determines how [Flow]s are forwarded.
     */
    val portSelectionPolicy: PortSelectionPolicy

    val fairnessPolicy: FairnessPolicy

    val flowHandler: FlowHandler

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routingTable: RoutingTable

    /**
     * Maps each connected [Node]'s id to the [Port] is connected to.
     */
    val portToNode: MutableMap<NodeId, Port>

    /**
     * Property returning the number of [Node]s connected to ***this***.
     */
    private val numOfConnectedNodes: Int
        get() { return ports.count { it.isConnected } }

    val updtChl: UpdateChl

//    val flowRates: MutableMap<FlowId, Kbps>

    suspend fun run(invalidator: StabilityValidator.Invalidator? = null) {
        invalidator?.let { updtChl.withInvalidator(invalidator) }
        updtChl.clear()

        while (true) {
            yield()
            consumeUpdt()
        }
    }

    suspend fun consumeUpdt() {
        var updt: RateUpdt = updtChl.receive()

        while (true)
            updtChl.tryReceiveSus().getOrNull()
                ?.also { updt = updt.merge(it) }
                ?: break

        with(flowHandler) { updtFlows(updt) }

        notifyAdjNodes()
    }

    suspend fun notifyAdjNodes() {
        portToNode.values.forEach { it.notifyReceiver() }
    }

    /**
     * Updates forwarding of all flows transiting through ***this*** node.
     */
    suspend fun updateAllFlows() {
        updtChl.send(RateUpdt(allTransitingFlowsIds().associateWith { DataRate.ZERO })) // TODO: change
    }


    override fun totIncomingDataRateOf(fId: FlowId): DataRate =
        flowHandler.outgoingFlows[fId]?.demand ?: DataRate.ZERO

    override fun totOutgoingDataRateOf(fId: FlowId): DataRate =
        flowHandler.outgoingFlows[fId]?.totRateOut ?: DataRate.ZERO

    override fun allTransitingFlowsIds(): Collection<FlowId> =
        with(flowHandler) {
            outgoingFlows.keys + receivingFlows.keys
        }


    companion object {
        private val log by logger()
    }
}

