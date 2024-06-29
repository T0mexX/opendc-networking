package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.utils.Kbps

/**
 * Keeps track of incoming and outgoing flows through a single [Node].
 * This structure is used by [ForwardingPolicy]s to determine forwarding actions.
 */
internal class FlowsTable {

    /**
     * Maps each [EndToEndFlow]'s ids to the corresponding incoming [Flow]s.
     * Each incoming flow which is part of the same [EndToEndFlow] is received from a different adjacent [Node].
     */
    private val incomingFlows: MutableMap<FlowId, MutableSet<Flow>> = HashMap()

    /**
     * Maps each [EndToEndFlow]'s id to the corresponding outgoing [Flow]s.
     * Each outgoing flow which is part of the same [EndToEndFlow] is sent to a different adjacent [Node].
     */
    private val outgoingFlows: MutableMap<FlowId, MutableSet<Flow>> = HashMap()

    /**
     * Adds an incoming flow.
     * @param[flow]     incoming flow to add.
     */
    fun addIncomingFLow(flow: Flow) {
        // TODO: add warning if flow with same id and sender is already present
        incomingFlows
            .getOrPut(flow.id) { HashSet() }
                .removeAll { it.id == flow.id && it.sender === flow.sender }
        incomingFlows[flow.id]?.add(flow)
    }

    /**
     * Adds outgoing flow.
     * @param[flow]     outgoing flow to add.
     */
    fun addOutgoingFlow(flow: Flow) {
        outgoingFlows.getOrPut(flow.id) { HashSet() }
            .add(flow)
    }

    /**
     * Returns all the outgoing flows that are part of the same [EndToEndFlow].
     * Each flow is forwarded to a different adjacent [Node].
     * @param[flowId]   [EndToEndFlow] of which all the outgoing [Flow]s are to be returned.
     */
    fun getOutGoingFlowsOf(flowId: FlowId): Set<Flow> =
        outgoingFlows[flowId] ?: setOf()

    /**
     * Returns the sum of the data rates of all incoming flows which belong to the same [EndToEndFlow].
     * @param[flowId]   [EndToEndFlow] id for which the total incoming data rate is to be returned.
     */
    fun totalIncomingDataOf(flowId: FlowId): Kbps =
        incomingFlows[flowId]?.sumOf { it.dataRate } ?: 0.0

    /**
     * Determines if the outgoing routs for the [EndToEndFlow]
     * corresponding to [flowId] have already been chosen.
     */
    fun hasOutgoingRoutsFor(flowId: FlowId): Boolean = outgoingFlows.containsKey(flowId)

    /**
     * Returns formatted string representing the outgoing flows of this [Node].
     */
    fun getFmtOutgoingFlows(): String {
        val sb = StringBuilder()
        sb.append("Outgoing Flows:\n")
        outgoingFlows.forEach { (flowId: FlowId, flowList: Set<Flow>) ->
            sb.append("  - Flow id $flowId:\n")
            flowList.forEach { flow -> sb.append("      $flow") }
        }
        return sb.toString()
    }

    /**
     * Returns formatted string representing the incoming flows of this [Node].
     */
    fun getFmtIngoingFlows(): String {
        val sb = StringBuilder()
        sb.append("Ingoing Flows:")
        incomingFlows.forEach { (flowId: FlowId, flowList: Set<Flow>) ->
            sb.append("  - Flow id $flowId:")
            flowList.forEach { flow -> sb.append("      $flow") }
        }
        return sb.toString()
    }
}
