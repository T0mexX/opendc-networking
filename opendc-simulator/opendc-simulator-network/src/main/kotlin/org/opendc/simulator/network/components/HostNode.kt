package org.opendc.simulator.network.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.energy.emodels.ClusterDfltEnModel
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.Kbps

/**
 * Represent an [EndPointNode] cluster with hosts.
 * TODO: integrate with current hosts implementation, now only useful for testing and playground.
 *
 */
internal class HostNode(
    override val id: NodeId,
    override val portSpeed: Kbps,
    override val numOfPorts: Int = 1,
    override val forwardingPolicy: ForwardingPolicy = StaticECMP
) : EnergyConsumer<HostNode>, EndPointNode {
    override val outgoingEtoEFlows: MutableMap<FlowId, NetFlow> = HashMap()

    override val incomingEtoEFlows: MutableMap<FlowId, NetFlow> = HashMap()

    override val routingTable: RoutingTable = RoutingTable(this.id)

    override val portToNode: MutableMap<NodeId, Port> = HashMap()

    override val enMonitor by lazy { EnMonitor(this) }

    override val ports: List<Port> =
        buildList { repeat(numOfPorts) {
            add(Port(speed = portSpeed, node = this@HostNode))
        } }


    override fun getDfltEnModel(): EnModel<HostNode> = ClusterDfltEnModel

    override fun toString(): String = "[HostNode: id=$id]"

    @Serializable
    @SerialName("host-node-specs")
    internal data class HostNodeSpec(
        val id: NodeId?,
        val portSpeed: Kbps,
        val numOfPorts: Int = 1
    ): Specs<HostNode> {
        override fun buildFromSpecs(): HostNode =
            HostNode(id = id ?: IdDispenser.nextNodeId, portSpeed = portSpeed, numOfPorts = numOfPorts)
    }
}


