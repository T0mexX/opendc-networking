package org.opendc.simulator.network.flow.tracker

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.OutFlow
import java.util.TreeSet

internal interface TrackerMode {
    fun OutFlow.compare(other: OutFlow): Int

    fun OutFlow.shouldBeTracked(): Boolean

    companion object {

        // non overridable
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
