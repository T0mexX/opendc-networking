package org.opendc.simulator.network.energy

import org.opendc.simulator.network.components.stability.NetworkStabilityChecker.Key.getNetStabilityChecker
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.units.Time
import org.opendc.simulator.network.utils.OnChangeHandler
import kotlin.coroutines.coroutineContext
import kotlin.properties.Delegates

/**
 * Monitors the energy consumption of [monitored] of type `T`.
 * @param[monitored]    network component monitored by ***this***.
 * @param[enModel]      energy model to use to compute current energy consumption for [monitored] network component.
 */
internal class EnMonitor<T: EnergyConsumer<T>>(
    private val monitored: T,
    private val enModel: EnModel<T> = monitored.getDfltEnModel()
) {
    /**
     * Callback functions of the observers of the [currPwrUsage] field.
     */
    private val obs: MutableList<OnChangeHandler<EnMonitor<T>, Power>> = mutableListOf()

    /**
     * The current energy consumption of the [monitored] network component.
     */
    var currPwrUsage: Power by Delegates.observable(Power.ZERO) { _, oldValue, newValue ->
        obs.forEach { it.handleChange(this, oldValue, newValue) }
    }
        private set

    var avrgPwrUsage: Power = Power.ZERO
        private set
    private var totTimeElapsed: Time = Time.ZERO

    var totEnConsumpt: Energy = Energy.ZERO
        private set

    /**
     * Adds an observer callback function.
     * @param[f]    callback function of the new observer.
     */
    fun onPwrUseChange(f: OnChangeHandler<EnMonitor<T>, Power>) { obs.add(f) }

    /**
     * Updates the energy consumption of [monitored].
     *
     * Ideally called by [monitored] whenever its state changes.
     */
    fun update() { currPwrUsage = enModel.computeCurrConsumpt(monitored) }

    suspend fun advanceBy(deltaTime: Time) =
        coroutineContext.getNetStabilityChecker().checkIsStableWhile {
            // Update total energy consumption
            totEnConsumpt += currPwrUsage * deltaTime

            // Update average power usage.
            avrgPwrUsage = ((avrgPwrUsage * totTimeElapsed.toSec()) + currPwrUsage * deltaTime.toSec()) / (totTimeElapsed + deltaTime).toSec()
            totTimeElapsed += deltaTime
        }
}
