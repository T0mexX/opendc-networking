package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.tracker.TrackerMode.Companion.setUp
import org.opendc.simulator.network.utils.approxLarger
import org.opendc.simulator.network.utils.logger
import java.util.TreeSet

/**
 * Keeps track of those flows whose demand is not satisfied,
 * maintaining them ordered by their data rate output.
 */
internal class FlowTracker(
    private val allOutgoingFlows: Map<FlowId, OutFlow>,
    vararg modes: TrackerMode
) {

    private val treesByMode = mutableMapOf<TrackerMode, TreeSet<OutFlow>>()

    init {
        modes.forEach { trackMode ->
            treesByMode.computeIfAbsent(trackMode) {
                trackMode.setUp(allOutgoingFlows)
            }
        }
    }

    operator fun plus(mode: TrackerMode) {
        treesByMode.computeIfAbsent(mode) { mode.setUp(allOutgoingFlows) }
    }

    operator fun minus(mode: TrackerMode) {
        treesByMode.remove(mode) ?: log.warn("unable to remove tracker mode $mode, mode not set")
    }

    /**
     * @return   [List] that contains [OutFlow]s that are tracked
     * based on [mode], sorted by the comparator defined in [mode]
     */
    operator fun get(mode: TrackerMode = treesByMode.keys.first()): List<OutFlow> {
        this + mode
        return treesByMode[mode]?.toList()!!
    }

    fun remove(outFlow: OutFlow) {
        treesByMode.values.forEach { it.remove(outFlow) }
    }









//    val unsatisfiedFlowsSortedByRate: List<OutFlow> get() = sortedSet.toList()


    /**
     * @return  the smaller [OutFlow] (based on the [mode]) among
     * those flows that are higher in the order than [outFlow] if it exists, else `null`.
     */
    fun nextHigherThan(
        outFlow: OutFlow,
        mode: TrackerMode = treesByMode.keys.first()
    ): OutFlow? {
        this + mode
        val treeSet: TreeSet<OutFlow> = treesByMode[mode]!!
        var curr = outFlow
        do {
            curr = treeSet.higher(curr) ?: return null
        } while (curr.totRateOut.approxLarger(outFlow.totRateOut).not())

        return curr
    }

    /**
     * This method should be invoked every time [OutFlow.demand]
     * or [OutFlow.totRateOut] fields need to be updated.
     * The actual field update shall be executed in the [fieldChanger] block.
     *
     * This method keeps the sorted sets for each tracker mode updated, determining if an element
     * should be added (or its order updated) in the sortedSet.
     */
    fun OutFlow.handlePropChange(fieldChanger: () -> Unit) {
        fun OutFlow.isNew(): Boolean = demand == .0 && totRateOut == .0

        treesByMode.forEach { (mode, tree) ->
            with(mode) {
                // If condition is true, then element should be in the sortedSet and be removed.
                // After this 'if' clause the OutFlow should never be in the tree.
                // The removal of the flow has to be executed before field is updated.
                if (shouldBeTracked())
                    tree.remove(this@handlePropChange).let {
                        if (isNew().not() && !it) log.warn("outflow ${this@handlePropChange} should have been in the sortedSet but wasn't")
                    }
            }
        }

        // Updates OutFlow field (either demand or totRateOut)
        fieldChanger()

        treesByMode.forEach { (mode, tree) ->
            with(mode) {
                // If condition is true, then element should be added to the treeSet,
                // since it is eligible for data rate increases.
                if (shouldBeTracked())
                    tree.add(this@handlePropChange)
            }
        }
    }

    companion object { val log by logger() }
}
