package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.OutFlow

internal object AllByDemand: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        this.demand.compareTo(other.demand)

    override fun OutFlow.shouldBeTracked(): Boolean =
        true
}
