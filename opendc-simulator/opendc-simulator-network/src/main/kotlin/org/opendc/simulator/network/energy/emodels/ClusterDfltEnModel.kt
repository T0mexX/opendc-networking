package org.opendc.simulator.network.energy.emodels

import org.opendc.simulator.network.components.Cluster
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.utils.Watts

/**
 * Dummy to be removed.
 */
internal object ClusterDfltEnModel: EnModel<Cluster> {
    override fun computeCurrConsumpt(enConsumer: Cluster): Watts = .0
}
