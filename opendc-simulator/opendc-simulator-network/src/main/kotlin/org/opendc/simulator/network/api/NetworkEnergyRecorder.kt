package org.opendc.simulator.network.api

import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.utils.KWh
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.W
import org.opendc.simulator.network.utils.Wh
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import java.time.Duration

public class NetworkEnergyRecorder internal constructor(consumers: List<EnergyConsumer<*>>) {
    private companion object { private val log by logger() }

    public var currentConsumption: W = .0
        private set
    public var totalConsumption: KWh = .0
        private set

    private val consumers: Map<NodeId, EnergyConsumer<*>> = consumers.associateBy { it.id }

    private val energyUseOnChangeHandler = OnChangeHandler<EnMonitor<*>, W> { _, oldValue, newValue ->
        currentConsumption += newValue - oldValue
    }

    init {
        consumers.forEach { it.enMonitor.addObserver(energyUseOnChangeHandler) }
        consumers.forEach { it.enMonitor.update() }
    }

    internal fun getFmtReport(): String {
        // TODO: change/improve
        return """
            === ENERGY REPORT ===
            Current Power Usage: ${currentConsumption} W
            Total EnergyConsumed: ${totalConsumption} KWh
            =====================
        """.trimIndent()
    }

    internal fun advanceBy(ms: ms) {
        fun ms.toHours(): Double = this.toDouble() / 1000 / 60 / 60

        totalConsumption += currentConsumption * ms.toHours() / 1000
    }

    internal fun reset() {
        totalConsumption = .0
    }
}
