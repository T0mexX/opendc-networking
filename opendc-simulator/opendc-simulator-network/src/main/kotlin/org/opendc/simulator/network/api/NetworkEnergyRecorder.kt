package org.opendc.simulator.network.api

import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.units.Time
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.logger

public class NetworkEnergyRecorder internal constructor(network: Network) {
    private companion object { private val log by logger() }

    public var currentConsumption: Power = Power.ZERO
        private set
    public var totalConsumption: Energy = Energy.ZERO
        private set

    private val consumers: MutableMap<NodeId, EnergyConsumer<*>> =
        network.nodes.values.filterIsInstance<EnergyConsumer<*>>().associateBy { it.id }.toMutableMap()

    private val powerUseOnChangeHandler = OnChangeHandler<EnMonitor<*>, Power> { _, oldValue, newValue ->
        currentConsumption += newValue - oldValue
    }

    init {
        consumers.values.forEach { it.enMonitor.addObserver(powerUseOnChangeHandler) }
        consumers.values.forEach { it.enMonitor.update() }

        network.onNodeAdded { _, node ->
            (node as? EnergyConsumer<*>)?.let { newConsumer ->
                newConsumer.enMonitor.addObserver(powerUseOnChangeHandler)
                consumers.compute(newConsumer.id) { _, oldConsumer ->
                    // If new consumer replaces an old one log warning msg
                    oldConsumer?.let { if (oldConsumer !== newConsumer) log.warn("energy consumer $oldConsumer is being replaced by $newConsumer which has the same id") }
                    newConsumer
                }
            }
        }
        network.onNodeRemoved { _, node ->
            (node as? EnergyConsumer<*>)?.let {
                consumers.remove(it.id)
                    ?: log.warn("energy consumer was removed from the network $network, but it was not tracked by the energy recorder")
            }
        }
    }

    internal fun getFmtReport(): String {
        return "\n" + """
            | === ENERGY REPORT ===
            | Current Power Usage: $currentConsumption
            | Total EnergyConsumed: $totalConsumption
        """.trimIndent()
    }

    internal fun advanceBy(ms: Time) {
        totalConsumption += currentConsumption * ms
    }

    internal fun reset() {
        totalConsumption = Energy.ZERO
    }
}
