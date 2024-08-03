package org.opendc.simulator.network.api

import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.units.Time
import org.opendc.simulator.network.units.times
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.logger

public class NetworkEnergyRecorder internal constructor(consumers: List<EnergyConsumer<*>>) {
    private companion object { private val log by logger() }

    public var currentConsumption: Power = Power.ZERO
        private set
    public var totalConsumption: Energy = Energy.ZERO
        private set

    private val consumers: Map<NodeId, EnergyConsumer<*>> = consumers.associateBy { it.id }

    private val energyUseOnChangeHandler = OnChangeHandler<EnMonitor<*>, Power> { _, oldValue, newValue ->
        currentConsumption += newValue - oldValue
    }

    init {
        consumers.forEach { it.enMonitor.addObserver(energyUseOnChangeHandler) }
        consumers.forEach { it.enMonitor.update() }
    }

    internal fun getFmtReport(): String {
        // TODO: change/improve
        return "\n" + """
            | === ENERGY REPORT ===
            | Current Power Usage: $currentConsumption
            | Total EnergyConsumed: $totalConsumption
        """.trimIndent()
    }

//    internal fun advanceBy(ms: ms) {
//        advanceBy(ms = Time.ofMillis(ms))
//    }

    internal fun advanceBy(ms: Time) {
        totalConsumption += currentConsumption * ms
    }

    internal fun reset() {
        totalConsumption = Energy.ZERO
    }
}
