package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.utils.approxLarger

internal object UnsatisfiedByRateOut: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        this.totRateOut.compareTo(other.totRateOut)

    override fun OutFlow.shouldBeTracked(): Boolean =
        demand.approxLarger(totRateOut)
}
