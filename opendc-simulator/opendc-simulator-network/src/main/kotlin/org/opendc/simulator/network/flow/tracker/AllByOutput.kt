package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.OutFlow

internal class AllByOutput: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        this.totRateOut.compareTo(other.totRateOut)

    override fun OutFlow.shouldBeTracked(): Boolean =
        true
}
