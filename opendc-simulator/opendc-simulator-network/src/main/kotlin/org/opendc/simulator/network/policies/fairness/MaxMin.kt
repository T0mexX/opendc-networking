package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.UnsatisfiedFlowsTracker
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.roundTo0ifErr

internal object MaxMin: FairnessPolicy {
    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        execRateReductions(updt)

        val tracker: UnsatisfiedFlowsTracker = unsatisfiedFlowsTracker
        var availableBW = this.availableBW

        while (true) {
            // Fetch updated list.
            val flows = tracker.unsatisfiedFlowsSortedByRate

            if (flows.isEmpty()) return

            // The lowest data rate among those that are higher than the first one.
            // Before increasing rates further, all lower unsatisfied rates should
            // reach either their demand or the ceiling.
            val currCeil: Kbps = tracker.nextHigherRateThan(flows.first()) ?: Double.MAX_VALUE

            val bandwidthSnapshot = availableBW
            with(flows) { forEach {
                // If ceiling reached, then continue with next target.
                if (it.totRateOut.approx(currCeil)) return@with
                if (availableBW.approx(.0)) return

                val prevRate = it.totRateOut
                val afterUpdt = it.tryUpdtRate(newRate = kotlin.math.min(currCeil, it.demand))

                availableBW -= (afterUpdt - prevRate).roundTo0ifErr()
            } }

            // If iteration did not increase data rate then the flows that necessitate
            // higher rate are outputted on maxed out ports (even though some ports are not maxed out).
            if (availableBW.approx(bandwidthSnapshot)) return
        }
    }
}