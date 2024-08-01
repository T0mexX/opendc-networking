package org.opendc.simulator.network.api

import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.KWh
import org.opendc.simulator.network.units.Ms
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.units.Watts
import org.opendc.simulator.network.units.times
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.logger

public class NetworkEnergyRecorder internal constructor(consumers: List<EnergyConsumer<*>>) {
    private companion object { private val log by logger() }

    public var currentConsumption: Power<*> = Watts(.0)
        private set

    public var totalConsumption: Energy<*> = KWh(.0)
        private set

    private val consumers: Map<NodeId, EnergyConsumer<*>> = consumers.associateBy { it.id }

    private val energyUseOnChangeHandler = OnChangeHandler<EnMonitor<*>, Watts> { _, oldValue, newValue ->
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

    internal fun advanceBy(ms: Ms) {
        totalConsumption += currentConsumption * ms
    }

    internal fun reset() {
        totalConsumption = KWh(.0)
    }
}
