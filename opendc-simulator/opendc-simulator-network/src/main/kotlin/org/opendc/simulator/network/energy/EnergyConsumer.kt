package org.opendc.simulator.network.energy

/**
 * Classes that implement this interface consume energy,
 * and their energy consumption can be tracked using [enMonitor].
 */
internal interface EnergyConsumer<T: EnergyConsumer<T>> {
    /**
     * Allows to track the energy consumption of ***this***.
     */
    val enMonitor: EnMonitor<T>

    /**
     * Returns the default energy model to use for ***this*** component,
     * if no specific energy model is provided.
     */
    fun getDfltEnModel(): EnModel<T>
}
