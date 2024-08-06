package org.opendc.simulator.network.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.stability.NetworkStabilityValidator
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.fairness.MaxMinPerPort
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.utils.IdDispenser

/**
 * Switch that also implements [EndPointNode].
 * This switch is able to start and receive [NetFlow]s.
 */
internal class CoreSwitch(
    id: NodeId,
    portSpeed: DataRate,
    numOfPorts: Int,
    fairnessPolicy: FairnessPolicy = MaxMinPerPort,
    portSelectionPolicy: PortSelectionPolicy = StaticECMP,
): Switch(id, portSpeed, numOfPorts, fairnessPolicy, portSelectionPolicy), EndPointNode {

    override suspend fun consumeUpdt() {
        super<EndPointNode>.consumeUpdt()
        enMonitor.update()
    }

    override suspend fun run(invalidator: NetworkStabilityValidator.Invalidator?) {
        return super<EndPointNode>.run(invalidator)
    }


    /**
     * Serializable representation of the specifics from which a core switch can be built.
     * Core switches in [CustomNetwork]s are automatically connected to the internet.
     */
    @Serializable
    @SerialName("core-switch-specs")
    internal data class CoreSwitchSpecs (
        val numOfPorts: Int,
        val portSpeed: DataRate,
        val id: NodeId? = null
    ): Specs<CoreSwitch> {
        override fun build(): CoreSwitch = CoreSwitch(id = id ?: IdDispenser.nextNodeId, portSpeed = portSpeed, numOfPorts + 1)
    }
}
