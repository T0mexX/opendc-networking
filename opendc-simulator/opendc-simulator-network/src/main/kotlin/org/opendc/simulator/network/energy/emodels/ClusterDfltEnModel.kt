package org.opendc.simulator.network.energy.emodels

import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.utils.Watts

/**
 * Dummy to be removed.
 */
internal object ClusterDfltEnModel: EnModel<HostNode> {
    override fun computeCurrConsumpt(enConsumer: HostNode): Watts = .0
}
