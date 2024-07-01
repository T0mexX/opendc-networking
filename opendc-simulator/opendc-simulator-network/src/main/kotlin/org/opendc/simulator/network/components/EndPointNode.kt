package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

/**
 * Node you can start a [Flow] from or direct a [Flow] to.
 * Updates the end-to-end data rate for each [EndToEndFlow] it is the receiver of.
 */
internal interface EndPointNode: Node {
    /**
     * Keeps track of outgoing [EndToEndFlow].
     */
    val outgoingEtoEFlows: MutableMap<NodeId, EndToEndFlow>

    /**
     * Keeps track of incoming [EndToEndFlow].
     */
    val incomingEtoEFlows: MutableMap<NodeId, EndToEndFlow>


    /**
     * Starts a [EndToEndFlow] from ***this*** node.
     * @param[netEtoEFlow]  the [EndToEndFlow] to be started.
     */
    fun startFlow(etoEFlow: EndToEndFlow) {
        // TODO: change
        outgoingEtoEFlows[etoEFlow.flowId] = etoEFlow
        forwardingPolicy.forwardFlow(forwarder = this, etoEFlow.flowId, etoEFlow.destId)
    }

    /**
     * Stores a reference to an incoming [EndToEndFlow] so that its end-to-end data rate can be updated.
     * @param[netEtoEFlow]  the [EndToEndFlow] to store the reference of.
     */
    fun addReceivingEtoEFlow(netEtoEFlow: EndToEndFlow) {
        incomingEtoEFlows[netEtoEFlow.flowId] = netEtoEFlow
    }


    override fun notifyFlowChange(flow: Flow) {
        incomingEtoEFlows[flow.id]?. let { updateEndToEndFlowDataRate(flow.id) }
            ?: let { super<Node>.notifyFlowChange(flow) }
    }

    override fun totDataRateOf(flowId: FlowId): Kbps =
        super<Node>.totDataRateOf(flowId) + (outgoingEtoEFlows[flowId]?.desiredDataRate ?: .0)

    /**
     * Updates the end-to-end data rate of a [EndToEndFlow] if the flow is expected to be received, else throws error.
     * @param[endToEndFlowId]   the flowId of the [EndToEndFlow] to update the data rate of.
     */
    private fun updateEndToEndFlowDataRate(endToEndFlowId: FlowId) {
        val endToEndFlow: EndToEndFlow = incomingEtoEFlows[endToEndFlowId] ?: throw IllegalArgumentException()
        endToEndFlow.currDataRate = totDataRateOf(endToEndFlowId)
    }
}
