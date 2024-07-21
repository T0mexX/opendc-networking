package org.opendc.simulator.network.flow

import org.opendc.simulator.network.policies.fairness.MaxMin
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.approxLarger
import org.opendc.simulator.network.utils.logger
import java.util.SortedSet
import java.util.TreeSet

/**
 * Keeps track of those flows whose demand is not satisfied,
 * maintaining them ordered by their data rate output.
 */
internal class UnsatisfiedFlowsTracker {

    /**
     * This [List] contains [OutFlow]s that are eligible for a data rate
     * increase (demand > rateOut using [approxLarger]), **sorted by their [OutFlow.totRateOut]**.
     */
    val unsatisfiedFlowsSortedByRate: List<OutFlow> get() = sortedSet.toList()

    /**
     * This [SortedSet] contains [OutFlow]s that are eligible for a data rate
     * increase (demand > rateOut using [approxLarger]), ordered by their [OutFlow.totRateOut].
     *
     * The custom comparator allows multiple flows to have the same output rate, despite it being a sortedSet.
     * When 2 flows have the same output rate then they are compared by hashCode (override in OutFlow),
     * and if still equal, compared by identityHashCode. The likelihood of 2 flows being equal
     * in all 3 comparisons is basically 0.
     */
    private val sortedSet: TreeSet<OutFlow> = TreeSet<OutFlow> { a, b ->
        if (a === b) 0
        else a.totRateOut.compareTo(b.totRateOut).let { outerIt ->
            if (outerIt == 0) {
                (a.hashCode() - b.hashCode()).let {
                    if (it == 0) System.identityHashCode(a) - System.identityHashCode(b)
                    else it
                }
            } else outerIt
        }
    }

    /**
     * @return  the lowest output data rate among
     * those flows that have a higher data rate than [outFlow] if it exists, else `null`.
     *
     * Useful for [MaxMin] fairness etc.
     */
    fun nextHigherRateThan(outFlow: OutFlow): Kbps? {
        var curr = outFlow
        do {
            curr = sortedSet.higher(curr) ?: return null
        } while (curr.totRateOut.approxLarger(outFlow.totRateOut).not())

        return curr.totRateOut
    }

    /**
     * This method should be invoked every time [OutFlow.demand]
     * or [OutFlow.totRateOut] fields need to be updated.
     * The actual field update shall be executed in the [fieldChanger] block.
     *
     * This method keeps [sortedSet] updated, determining if an element
     * should be added (or its order updated) in the sortedSet.
     * If the updated fields `demand` and `totRateOut` are such that (demand > totRateOut),
     * at the end of the method the element will be in the set and its order correct.
     */
    fun OutFlow.handlePropChange(fieldChanger: () -> Unit) {

        // If condition is true, then element should be in the sortedSet and be removed.
        // After this 'if' clause the OutFlow should never be in the sortedSet.
        // The removal of the flow has to be executed before totRateOut field is updated.
        if (demand.approxLarger(totRateOut))
            sortedSet.remove(this).let {
                if (it.not()) log.warn("outflow $this should have been in the sortedSet but wasn't")
            }

        // Updates OutFlow field (either demand or totRateOut)
        fieldChanger()

        // If condition is true, then element should be added to the sortedSet,
        // since it is eligible for data rate increases.
        if (demand.approxLarger(totRateOut))
            sortedSet.add(this)
    }

    companion object { val log by logger() }
}
