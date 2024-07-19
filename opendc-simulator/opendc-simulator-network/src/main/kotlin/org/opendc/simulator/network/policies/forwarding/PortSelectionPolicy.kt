package org.opendc.simulator.network.policies.forwarding

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowId

// TODO: documentation
internal fun interface PortSelectionPolicy {
    suspend fun Node.selectPorts(flowId: FlowId): Set<Port>
}
