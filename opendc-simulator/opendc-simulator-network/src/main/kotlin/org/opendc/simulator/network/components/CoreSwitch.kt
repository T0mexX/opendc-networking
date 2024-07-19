package org.opendc.simulator.network.components

import kotlinx.coroutines.Job
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

/**
 * Switch that also implements [EndPointNode].
 * This switch is able to start and receive [NetFlow]s.
 */
internal class CoreSwitch(
    id: NodeId,
    portSpeed: Kbps,
    numOfPorts: Int
): Switch(id, portSpeed, numOfPorts), EndPointNode {

    override suspend fun consumeUpdt() {
        super<EndPointNode>.consumeUpdt()
        enMonitor.update()
    }

    override suspend fun run(invalidator: StabilityValidator.Invalidator?) {
        return super<EndPointNode>.run(invalidator)
    }
}
