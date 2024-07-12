package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.Result.ERROR

internal class Internet(
    override val forwardingPolicy: ForwardingPolicy = StaticECMP
): EndPointNode {

    override val id: NodeId = INTERNET_ID

    companion object {

        // not needed anymore
//        fun excludingIds(ids: Collection<NodeId>): Internet {
//            var internetId = NodeId.MIN_VALUE
//            while (internetId in ids) internetId++
//
//            return Internet(id = internetId)
//        }
    }

    fun connectedTo(coreSwitches: Collection<CoreSwitch>): Internet {
        coreSwitches.forEach {
            check (it.connect(this) !is ERROR)
            { "unable to connect core switches to internet, be aware that core switches need an extra port" }
        }

        return this
    }

    override fun connect(other: Node): Result {
        addPort()
        return super.connect(other)
    }

    private fun addPort() { ports.add(Port(speed = portSpeed, node = this)) }

    override val outgoingEtoEFlows = mutableMapOf<FlowId, NetFlow>()
    override val incomingEtoEFlows = mutableMapOf<FlowId, NetFlow>()
    override val portSpeed: Kbps = Kbps.MAX_VALUE
    override val ports = mutableListOf<Port>()
        get() {
            if (portToNode.size == field.size)
                field.add(Port(speed = portSpeed, node = this))
            return field
        }
    override val routingTable = RoutingTable(this.id)

    override val portToNode = mutableMapOf<NodeId, Port>()
}
