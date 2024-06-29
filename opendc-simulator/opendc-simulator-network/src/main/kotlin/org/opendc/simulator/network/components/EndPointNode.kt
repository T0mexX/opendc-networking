package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.OnChangeHandler
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

    override val dataRateOnChangeHandler: OnChangeHandler<Flow, Kbps>
        /**
         * The getter is needed to be able to define in interface.
         * Call the getter only once per instance to avoid overhead.
         */
        get() {
            return OnChangeHandler { inFlow, old, new ->
                if (old == new) return@OnChangeHandler
                updateEndToEndFlowDataRate(inFlow.id)
            }
        }

    /**
     * Starts a [EndToEndFlow] from ***this*** node.
     * @param[netEtoEFlow]  the [EndToEndFlow] to be started.
     */
    fun startFlow(netEtoEFlow: EndToEndFlow) {
        // TODO: change
        outgoingEtoEFlows[netEtoEFlow.flowId] = netEtoEFlow
        forwardingPolicy.forwardFlow(forwarder = this, netEtoEFlow.getInitialFlow(sender = this))
    }

    /**
     * Stores a reference to an incoming [EndToEndFlow] so that its end-to-end data rate can be updated.
     * @param[netEtoEFlow]  the [EndToEndFlow] to store the reference of.
     */
    fun addReceivingEtoEFlow(netEtoEFlow: EndToEndFlow) {
        incomingEtoEFlows[netEtoEFlow.flowId] = netEtoEFlow
    }

    override fun pushNewFlow(flow: Flow) {

        if (flow.finalDestId == this.id) {
            flowTable.addIncomingFLow(flow)
            updateEndToEndFlowDataRate(flow.id)
            flow.addDataRateObsChangeHandler(this.dataRateOnChangeHandler)
        } else {
            outgoingEtoEFlows[flow.id]
            super<Node>.pushNewFlow(flow)
        }
    }

    /**
     * Updates the end-to-end data rate of a [EndToEndFlow] if the flow is expected to be received, else throws error.
     * @param[endToEndFlowId]   the flowId of the [EndToEndFlow] to update the data rate of.
     */
    private fun updateEndToEndFlowDataRate(endToEndFlowId: FlowId) {
        val endToEndFlow: EndToEndFlow = incomingEtoEFlows[endToEndFlowId] ?: throw IllegalArgumentException()
        endToEndFlow.currDataRate = flowTable.totalIncomingDataOf(endToEndFlowId)
    }
}
