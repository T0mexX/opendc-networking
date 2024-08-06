package org.opendc.simulator.network.api

import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.stability.NetworkStabilityChecker.Key.getNetStabilityChecker
import org.opendc.simulator.network.energy.EnMonitor
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.units.Time
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.logger
import kotlin.coroutines.coroutineContext

public class NetworkEnergyRecorder internal constructor(network: Network) {
    private companion object { private val log by logger() }

    public var currPwrUsage: Power = Power.ZERO
        private set

    public var avrgPwrUsage: Power = Power.ZERO
        private set
    private var totTimeElapsed: Time = Time.ZERO

    public var totalConsumption: Energy = Energy.ZERO
        private set

    private val consumersById: MutableMap<NodeId, EnergyConsumer<*>> =
        network.nodes.values.filterIsInstance<EnergyConsumer<*>>().associateBy { it.id }.toMutableMap()

    private val powerUseOnChangeHandler = OnChangeHandler<EnMonitor<*>, Power> { _, oldValue, newValue ->
        currPwrUsage += newValue - oldValue
    }

    init {
        consumersById.values.forEach { it.enMonitor.onPwrUseChange(powerUseOnChangeHandler) }
        consumersById.values.forEach { it.enMonitor.update() }

        network.onNodeAdded { _, node ->
            (node as? EnergyConsumer<*>)?.let { newConsumer ->
                newConsumer.enMonitor.onPwrUseChange(powerUseOnChangeHandler)
                consumersById.compute(newConsumer.id) { _, oldConsumer ->
                    // If new consumer replaces an old one log warning msg
                    oldConsumer?.let { if (oldConsumer !== newConsumer) log.warn("energy consumer $oldConsumer is being replaced by $newConsumer which has the same id") }
                    newConsumer
                }
            }
        }
        network.onNodeRemoved { _, node ->
            (node as? EnergyConsumer<*>)?.let {
                consumersById.remove(it.id)
                    ?: log.warn("energy consumer was removed from the network $network, but it was not tracked by the energy recorder")
            }
        }
    }

    internal fun getFmtReport(): String {
        return "\n" + """
            | === ENERGY REPORT ===
            | Current Power Usage: $currPwrUsage
            | Total EnergyConsumed: $totalConsumption
        """.trimIndent()
    }

    internal suspend fun advanceBy(deltaTime: Time) {
        coroutineContext.getNetStabilityChecker().checkIsStableWhile {
            // Update total energy consumption.
            totalConsumption += currPwrUsage * deltaTime

            // Advance time of each node's energy monitor.
            consumersById.values.forEach { it.enMonitor.advanceBy(deltaTime) }

            // Update average power usage.
            avrgPwrUsage = ((avrgPwrUsage * totTimeElapsed.toSec()) + currPwrUsage * deltaTime.toSec()) / (totTimeElapsed + deltaTime).toSec()
            totTimeElapsed += deltaTime
        }
    }

    internal fun reset() {
        totalConsumption = Energy.ZERO
    }
}
