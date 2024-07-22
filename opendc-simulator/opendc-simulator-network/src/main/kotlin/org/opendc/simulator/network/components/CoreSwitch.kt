package org.opendc.simulator.network.components

import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMin
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.Kbps

/**
 * Switch that also implements [EndPointNode].
 * This switch is able to start and receive [NetFlow]s.
 */
internal class CoreSwitch(
    id: NodeId,
    portSpeed: Kbps,
    numOfPorts: Int,
    fairnessPolicy: FairnessPolicy = MaxMin,
    portSelectionPolicy: PortSelectionPolicy = StaticECMP,
): Switch(id, portSpeed, numOfPorts, fairnessPolicy, portSelectionPolicy), EndPointNode {

    override suspend fun consumeUpdt() {
        super<EndPointNode>.consumeUpdt()
        enMonitor.update()
    }

    override suspend fun run(invalidator: StabilityValidator.Invalidator?) {
        return super<EndPointNode>.run(invalidator)
    }
}
