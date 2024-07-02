package org.opendc.simulator.network.policies.forwarding

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps


/**
 * Static Equal Cost Multi-Path. It forwards a [Flow] to all the links
 * that offer the same shortest number of hops to destination,
 * independently of the links' utilization.
 */
internal object StaticECMP: ForwardingPolicy {

    /**
     * The [EndToEndFlow] in the network. Needed to retrieve the destination id when forwarding a flow.
     */
    lateinit var eToEFlows: Map<FlowId, EndToEndFlow>

    override fun forwardFlow(forwarder: Node, flowId: FlowId) {
        val routTable: RoutingTable = forwarder.routingTable
        val portToNode: Map<NodeId, Port> = forwarder.portToNode
        val totDataRateToForward: Kbps = forwarder.totDataRateOf(flowId)
        val finalDestId: NodeId = eToEFlows[flowId]?.destId ?: throw IllegalStateException("unable to forward flow, flow id $flowId not recognized")


        val portsToForwardTo: List<Port> =
            routTable.getPossiblePathsTo(finalDestId)
                .onlyMinimal()
                .map { path -> portToNode[path.nextHop.id]!! }

        val portsToResetFlow: List<Port> = portToNode.values - portsToForwardTo.toSet()
        portsToResetFlow.forEach { it.resetAndRmFlowOut(flowId) }

        val rateForEachPort: Kbps = totDataRateToForward / portsToForwardTo.size

        portsToForwardTo.forEach { it.pushFlowIntoLink(
            flowId = flowId,
            finalDestId = finalDestId,
            dataRate = rateForEachPort
        ) }
    }

    /**
     * Filters ***this*** collection of [RoutingTable.PossiblePath], keeping only those that are minimal.
     */
    private fun Collection<RoutingTable.PossiblePath>.onlyMinimal(): Collection<RoutingTable.PossiblePath> {
        val min: Int = this.minOfOrNull { it.numOfHops } ?: 0
        return  this.filter { it.numOfHops == min }
    }
//    /**
//     * Splits a [Flow] in `n` [Flow]s,
//     * each with `dataRate` equal to `/n` the initial flow `dataRate`.
//     * @param[n]    number of sub-flows to split into.
//     */
//    private fun Flow.split(n: Int): List<Flow> {
//        return buildList {
//            repeat(n) {
//                add(this@split.copy(dataRate = this@split.dataRate / n))
//            }
//        }
//    }
}
