package org.opendc.simulator.network.api

import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.Watts
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms

public class NetworkEnergyRecorder internal constructor(consumers: List<EnergyConsumer<*>>) {
    private companion object { private val log by logger() }

    public var currentConsumption: Watts = .0
        private set
    public var totalConsumption: Watts = .0
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
        return """
            === ENERGY REPORT ===
            Current Energy Consumption: ${currentConsumption}W
            Total EnergyConsumption: ${totalConsumption}
            =====================
        """.trimIndent()
    }

    internal fun advanceBy(ms: ms) {
        fun ms.toSeconds(): Double = this.toDouble() / 1000

        totalConsumption += currentConsumption * ms.toSeconds()
    }

    internal fun reset() {
        totalConsumption = .0
    }
}
