package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.OutFlow
import java.util.TreeSet

internal interface TrackerMode {
    fun OutFlow.compare(other: OutFlow): Int

    fun OutFlow.shouldBeTracked(): Boolean

    companion object {

        // non overridable
        /**
         * Sets up the [TreeSet] of [OutFlow]s ([allOutgoingFlows]) to track in a specific node
         * (e.g. useful for fairness policies), based on the [compare] rule defined in concrete instances.
         *
         * This function is called whenever a new tracker is requested.
         */
        fun TrackerMode.setUp(allOutgoingFlows: Map<FlowId, OutFlow>): TreeSet<OutFlow> {
            val treeSet = TreeSet<OutFlow> { a, b ->
                if (a === b) 0
                else a.compare(other = b).let { outerIt ->
                    if (outerIt == 0) {
                        (a.hashCode() - b.hashCode()).let {
                            if (it == 0) System.identityHashCode(a) - System.identityHashCode(b)
                            else it
                        }
                    } else outerIt
                }
            }

            treeSet.addAll(allOutgoingFlows.values)
            return treeSet
        }
    }
}

internal object AllByDemand: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        this.demand.compareTo(other.demand)

    override fun OutFlow.shouldBeTracked(): Boolean =
        true
}

internal object AllByOutput: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        this.totRateOut.compareTo(other.totRateOut)

    override fun OutFlow.shouldBeTracked(): Boolean =
        true
}

internal object UnsatisfiedByRateOut: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        this.totRateOut.compareTo(other.totRateOut)

    override fun OutFlow.shouldBeTracked(): Boolean =
        demand.approxLarger(totRateOut)
}

internal object AllByUnsatisfaction: TrackerMode {
    override fun OutFlow.compare(other: OutFlow): Int =
        (this.totRateOut / this.demand).compareTo(other.totRateOut / other.demand)

    override fun OutFlow.shouldBeTracked(): Boolean =
        true
}
