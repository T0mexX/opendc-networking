package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.tracker.FlowTracker
import org.opendc.simulator.network.flow.tracker.UnsatisfiedByRateOut
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.utils.roundTo0withEps

internal object MaxMinNoForcedReduction: FairnessPolicy {
    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        execRateReductions(updt)


        val tracker: FlowTracker = flowTracker
        var availableBW = this.availableBW

        while (true) {
            // Fetch updated list.
            val flows = tracker[UnsatisfiedByRateOut]

            if (flows.isEmpty()) return

            // The lowest data rate among those that are higher than the first one.
            // Before increasing rates further, all lower unsatisfied rates should
            // reach either their demand or the ceiling.
            val currCeil: DataRate =
                tracker.nextHigherThan(flows.first())
                    ?.totRateOut ?: DataRate.ofKbps(Double.MAX_VALUE)

            val bandwidthSnapshot = availableBW
            with(flows) { forEach {
                // If ceiling reached, then continue with next target.
                if (it.totRateOut.approx(currCeil)) return@with
                if (availableBW.approxZero()) return

                val prevRate = it.totRateOut
                val afterUpdt = it.tryUpdtRate(newRate = currCeil min it.demand)

                availableBW -= (afterUpdt - prevRate).roundedTo0WithEps()
            } }

            // If iteration did not increase data rate then the flows that necessitate
            // higher rate are outputted on maxed out ports (even though some ports are not maxed out).
            if (availableBW.approx(bandwidthSnapshot)) return
        }
    }
}
