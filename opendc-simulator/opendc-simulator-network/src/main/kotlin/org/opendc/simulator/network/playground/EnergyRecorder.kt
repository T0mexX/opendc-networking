package org.opendc.simulator.network.playground

import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.Watts
import org.opendc.simulator.network.utils.logger

internal class NetworkEnergyRecorder(consumers: List<EnergyConsumer<*>>) {
    companion object { val log by logger() }

    var currentConsumption: Double = .0
        private set
    var totalConsumption: Double = .0
        private set
    private val consumers: Map<NodeId, EnergyConsumer<*>> = consumers.associateBy { it.id }
    private val energyUseOnChangeHandler = OnChangeHandler<EnMonitor<*>, Watts> { _, oldValue, newValue ->
        currentConsumption += newValue - oldValue
    }

    init { consumers.forEach { it.enMonitor.addObserver(energyUseOnChangeHandler) } }

    fun getFmtReport(): String {
        // TODO: change/improve
        return """
            === ENERGY REPORT ===
//            Total Energy Consumption: ${totalConsumption}kWh
            Current Energy Consumption: ${currentConsumption}kWh
            =====================
        """.trimIndent()
    }
}
