package org.opendc.simulator.network.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.port.PortImpl
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.units.DataRate

/**
 * Represent an [EndPointNode] cluster with hosts.
 * TODO: integrate with current hosts implementation, now only useful for testing and playground.
 *
 */
internal class HostNode(
    override val id: NodeId,
    override val portSpeed: DataRate<*>,
    override val numOfPorts: Int = 1,
    override val fairnessPolicy: FairnessPolicy = MaxMinPerPort,
    override val portSelectionPolicy: PortSelectionPolicy = StaticECMP,
): EndPointNode {

    override val updtChl = UpdateChl()


    override val routingTable: RoutingTable = RoutingTable(this.id)

    override val portToNode: MutableMap<NodeId, Port> = HashMap()

    override val ports: List<Port> =
        buildList { repeat(numOfPorts) {
            add(PortImpl(maxSpeed = portSpeed, owner = this@HostNode))
        } }
    override val flowHandler = FlowHandler(ports)

    override fun toString(): String = "[HostNode: id=$id]"

    @Serializable
    @SerialName("host-node-specs")
    internal data class HostNodeSpec <T: DataRate<T>> (
        val id: NodeId?,
        val portSpeed: DataRate<T>,
        val numOfPorts: Int = 1
    ): Specs<HostNode> {
        override fun build(): HostNode =
            HostNode(id = id ?: IdDispenser.nextNodeId, portSpeed = portSpeed, numOfPorts = numOfPorts)
    }
}


