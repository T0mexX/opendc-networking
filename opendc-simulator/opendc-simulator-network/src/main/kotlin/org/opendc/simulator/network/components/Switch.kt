package org.opendc.simulator.network.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.components.internalstructs.FlowsTable
import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.utils.IdDispatcher
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.emodels.SwitchDfltEnModel
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.Kbps

/**
 * A [Node] whose job is to route incoming flows according to [forwardingPolicy].
 * @param[id]               id of this [Node].
 * @param[portSpeed]        port speed in Kbps.
 * @param[numOfPorts]       number of ports.
 * @param[forwardingPolicy] policy used to determine the links to which forward incoming flows.
 */
internal open class Switch(
    final override val id: NodeId,
    override val portSpeed: Kbps,
    override val numOfPorts: Int,
    override val forwardingPolicy: ForwardingPolicy = StaticECMP,
): Node, EnergyConsumer<Switch> {

    override val enMonitor: EnMonitor<Switch> by lazy { EnMonitor(this) }
    override val routingTable: RoutingTable = RoutingTable(this.id)

    override val portToNode: MutableMap<NodeId, Port> = HashMap()
    override val ports: List<Port> =
        buildList { repeat(numOfPorts) {
            add(Port(speed = portSpeed, node = this@Switch))
        } }


    override fun notifyFlowChange(flowId: FlowId) {
        super<Node>.notifyFlowChange(flowId)
        enMonitor.update()
    }

    override fun getDfltEnModel(): EnModel<Switch> = SwitchDfltEnModel

    override fun toString(): String = "[Switch: id=$id]"


    /**
     * Serializable representation of the specifics from which a switch can be built.
     */
    @Serializable
    @SerialName("switch-specs")
    internal data class SwitchSpecs (
        val numOfPorts: Int,
        val portSpeed: Kbps,
        val id: NodeId? = null
    ): Specs<Switch> {
        override fun buildFromSpecs(): Switch = Switch(id = id ?: IdDispatcher.nextId, portSpeed, numOfPorts)

        fun buildCoreSwitchFromSpecs(): CoreSwitch = CoreSwitch(id = id ?: IdDispatcher.nextId, portSpeed, numOfPorts)
    }
}




