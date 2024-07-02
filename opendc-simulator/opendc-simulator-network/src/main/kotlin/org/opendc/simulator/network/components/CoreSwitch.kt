package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

/**
 * Switch that also implements [EndPointNode].
 * This switch is able to start and receive [EndToEndFlow]s.
 */
internal class CoreSwitch(
    id: NodeId,
    portSpeed: Kbps,
    numOfPorts: Int
): Switch(id, portSpeed, numOfPorts), EndPointNode {
    override val incomingEtoEFlows: MutableMap<NodeId, EndToEndFlow> = HashMap()
    override val outgoingEtoEFlows: MutableMap<NodeId, EndToEndFlow> = HashMap()

    override fun notifyFlowChange(flowId: FlowId) {
        super<EndPointNode>.notifyFlowChange(flowId)
        enMonitor.update()
    }
}
