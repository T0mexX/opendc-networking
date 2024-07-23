package org.opendc.simulator.network.policies.forwarding

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId


// TODO: documentation
internal object StaticECMP: PortSelectionPolicy {

    /**
     * The [NetFlow] in the network. Needed to retrieve the destination id when forwarding a flow.
     */
    lateinit var eToEFlows: Map<FlowId, NetFlow>

    private var avr = .0
    private var count = 0

    var nano: Long = 0
    override suspend fun Node.selectPorts(flowId: FlowId): Set<Port> {
        val finalDestId: NodeId = eToEFlows[flowId]?.destinationId
            ?: throw IllegalStateException("unable to forward flow, flow id $flowId not recognized")

        return routingTable.getPossiblePathsTo(finalDestId)
            .onlyMinimal()
            .map { with(it) { associatedPort()!! } }
            .toSet()
    }

    /**
     * Filters ***this*** collection of [RoutingTable.PossiblePath], keeping only those that are minimal.
     */
    private fun Collection<RoutingTable.PossiblePath>.onlyMinimal(): Collection<RoutingTable.PossiblePath> {
        val min: Int = this.minOfOrNull { it.numOfHops } ?: 0
        return  this.filter { it.numOfHops == min }
    }

    private fun RoutingTable.PossiblePath.associatedPort(node: Node): Port =
        node.portToNode[this.nextHop.id] !!
}
