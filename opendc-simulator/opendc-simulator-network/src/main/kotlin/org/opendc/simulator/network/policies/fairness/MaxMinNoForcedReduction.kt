package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.tracker.AllByDemand
import org.opendc.simulator.network.flow.tracker.FlowTracker
import org.opendc.simulator.network.flow.tracker.UnsatisfiedByRateOut
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.approxLarger
import org.opendc.simulator.network.utils.isSorted
import org.opendc.simulator.network.utils.roundTo0withEps

internal object MaxMinNoForcedReduction: FairnessPolicy {
    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        execRateReductions(updt)


        val tracker: FlowTracker = flowTracker
        var availableBW = this.availableBW
        val flows: List<OutFlow> = tracker[UnsatisfiedByRateOut]
        check(outgoingFlows.values.filter { it.demand approxLarger it.totRateOut }.all { it in flows })
        check(flows.isSorted { a, b -> a.totRateOut.compareTo(b.totRateOut) } )

        while (true) {
            // Fetch updated list.
            val flows = tracker[UnsatisfiedByRateOut]

            if (flows.isEmpty()) return

            // The lowest data rate among those that are higher than the first one.
            // Before increasing rates further, all lower unsatisfied rates should
            // reach either their demand or the ceiling.
            val currCeil: Kbps = tracker.nextHigherThan(flows.first())?.totRateOut ?: Double.MAX_VALUE

            val bandwidthSnapshot = availableBW
            with(flows) { forEach {
                // If ceiling reached, then continue with next target.
                if (it.totRateOut.approx(currCeil)) return@with
                if (availableBW.approx(.0)) return

                val prevRate = it.totRateOut
                val afterUpdt = it.tryUpdtRate(newRate = kotlin.math.min(currCeil, it.demand))

                availableBW -= (afterUpdt - prevRate).roundTo0withEps()
            } }

            // If iteration did not increase data rate then the flows that necessitate
            // higher rate are outputted on maxed out ports (even though some ports are not maxed out).
            if (availableBW.approx(bandwidthSnapshot)) return
        }
    }
}
