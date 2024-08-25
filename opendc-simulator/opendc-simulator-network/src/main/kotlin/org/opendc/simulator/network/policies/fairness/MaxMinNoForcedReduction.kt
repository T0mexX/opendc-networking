/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.policies.fairness

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.tracker.NodeFlowTracker
import org.opendc.simulator.network.flow.tracker.UnsatisfiedByRateOut

internal object MaxMinNoForcedReduction : FairnessPolicy {
    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        execRateReductions(updt)

        val tracker: NodeFlowTracker = nodeFlowTracker
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
            with(flows) {
                forEach {
                    // If ceiling reached, then continue with next target.
                    if (it.totRateOut.approx(currCeil)) return@with
                    if (availableBW.approxZero()) return

                    val prevRate = it.totRateOut
                    val afterUpdt = it.tryUpdtRate(newRate = currCeil min it.demand)

                    availableBW -= (afterUpdt - prevRate).roundToIfWithinEpsilon(DataRate.ZERO)
                }
            }

            // If iteration did not increase data rate then the flows that necessitate
            // higher rate are outputted on maxed out ports (even though some ports are not maxed out).
            if (availableBW.approx(bandwidthSnapshot)) return
        }
    }
}
