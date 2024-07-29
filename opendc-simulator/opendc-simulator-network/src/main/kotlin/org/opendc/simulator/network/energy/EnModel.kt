package org.opendc.simulator.network.energy

import org.opendc.simulator.network.utils.W

/**
 * Represents the energy model for a specific component of type `T`.
 */
internal fun interface EnModel<T: EnergyConsumer<T>> {

    /**
     * Computes the current energy consumption of ***this***.
     * @param[e]   energy consumer network component whose energy consumption is to be computed.
     */
    fun computeCurrConsumpt(e: T): W
}
