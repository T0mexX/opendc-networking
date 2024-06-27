package org.opendc.simulator.network.policies.forwarding

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.components.internalstructs.FlowsTable
import org.opendc.simulator.network.components.Link
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.internalstructs.RoutingTable


/**
 * Static Equal Cost Multi-Path. It forwards a [Flow] to all the links
 * that offer the same shortest number of hops to destination,
 * independently of the links' utilization.
 */
internal object StaticECMP: ForwardingPolicy {

    override fun forwardFlow(forwarder: Node, flow: Flow) {
        val routTable: RoutingTable = forwarder.routingTable
        val linksToAdjNodes: Map<NodeId, Link> = forwarder.linksToAdjNodes
        val flowTable: FlowsTable = forwarder.flowTable

        val linksToForwardTo: List<Link?> =
            routTable.getPossiblePathsTo(flow.finalDestinationId)
                ?.map { path -> linksToAdjNodes[path.nextHop.id] }!!
        flow.split(linksToForwardTo.size).forEachIndexed { idx, subFlow ->
            flowTable.addOutgoingFlow(subFlow)
            linksToForwardTo[idx]!!.pushNewFlow(subFlow)
        }
    }

    /**
     * Splits a [Flow] in `n` [Flow]s,
     * each with `dataRate` equal to `/n` the initial flow `dataRate`.
     * @param[n]    number of sub-flows to split into.
     */
    private fun Flow.split(n: Int): List<Flow> {
        return buildList {
            repeat(n) {
                add(this@split.copy(dataRate = this@split.dataRate / n))
            }
        }
    }
}
