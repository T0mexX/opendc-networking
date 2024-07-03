package org.opendc.simulator.network.energy

import org.opendc.simulator.network.utils.Watts

/**
 * Represents the energy model for a specific component of type `T`.
 */
internal fun interface EnModel<T: EnergyConsumer<T>> {

    /**
     * Computes the current energy consumption of ***this***.
     * @param[enConsumer]   energy consumer network component whose energy consumption is to be computed.
     */
    fun computeCurrConsumpt(e: T): Watts
}
