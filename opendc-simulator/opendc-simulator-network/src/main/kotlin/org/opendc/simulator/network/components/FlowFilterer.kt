package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.utils.forEachParallel
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min
import kotlin.system.measureNanoTime

/**
 * Filter incoming flows ([Filter.unfiltered]) so that each flow
 * gets bandwidth proportional to its unfiltered data rate (if the bandwidth is maxed out).
 */
internal abstract class FlowFilterer {
    companion object { private val log by logger() }

    /**
     * The next filter in a chain of filters. e.g [Port] -> [Link] -> [Port]
     */
    var nextFilter: FlowFilterer? = null

    /**
     * Maps each [FlowId] with its associated [Filter].
     */
    protected val filters: MutableMap<FlowId, Filter> = HashMap()

    /**
     * The maximum bandwidth of ***this*** medium. When bandwidth is
     * maxed out,filtered flows start to be lowered than unfiltered ones.
     */
    abstract val maxBW: Kbps

    /**
     * The [Flow]s transiting through
     * ***this*** medium after they have been filtered.
     */
    val filteredFlows: Map<FlowId, Flow>
        get() { return filters.mapValues { it.value.filtered } }

    /**
     * The list of the flows that have been changed by the last call to [updateFilters].
     * Useful for optimizations.
     */
    var lastUpdatedFlows: List<Flow> = listOf()

    /**
     * Updates each [Filter]. Each flow receives bandwidth
     * proportional to its [Filter.unfiltered] data rate.
     * The ids of the flows that are effectively changed
     * by this call are stored in [lastUpdatedFlows].
     * @param[newFlowId]    if exists, a new flow that was added before this call,
     * the resulting filtered flow is then added to [nextFilter].
     */
    open fun updateFilters(newFlowId: FlowId? = null ) {
        val lastUpdatedFlows = mutableListOf<Flow>()

        val totIncomingDataRate: Kbps = filters.values.sumOf { it.unfiltered.dataRate }

        val a = measureNanoTime {
            filters.values.filter { it.unfiltered.dataRate == .0 }
                .forEach {
                    lastUpdatedFlows.add(it.filtered)
                    resetAndRmFlow(it.id, updateFilters = false)
                }
        }

        val b = measureNanoTime {
            filters.values.forEach { filter ->
                val dedicatedDataRate: Kbps = dedicatedDataRateOf(filter.unfiltered, totIncomingDataRate)

                if (dedicatedDataRate != filter.filtered.dataRate) {
                    filter.filtered.dataRate = dedicatedDataRate
                    lastUpdatedFlows.add(filter.filtered)
                }
            }
        }

        this.lastUpdatedFlows = lastUpdatedFlows

        val newFilteredFlow: Flow? = filters[newFlowId]?.filtered
        newFilteredFlow?. let { nextFilter?.addOrReplaceFlow(newFilteredFlow) }

        nextFilter?.updateFilters()
    }

    /**
     * If a flow with same id is present, it is swapped with the new one.
     * Otherwise, the new flow is added and the callback [ifNew] is called with
     * the resulting filtered flow.
     * @param[flow]     flow to add.
     * @param[ifNew]    function to call if the flow was
     * not present already, with the filtered flow as arg.
     */
    open fun addOrReplaceFlow(flow: Flow, ifNew: (Flow) -> Unit = {}) {
        filters[flow.id]?. also {
            filters[flow.id] = Filter(unfiltered = flow, filtered = it.filtered)
            updateFilters()
        } ?: let {
            val newFilter = Filter(unfiltered = flow)
            filters[flow.id] = newFilter
            updateFilters(newFlowId = flow.id)
            ifNew(newFilter.filtered)
        }
    }

    /**
     * Removes the flow from this medium, also setting its
     * data rate to 0 so that [nextFilter] can act accordingly.
     * @param[flowId]           id of the flow to remove.
     * @param[updateFilters]    determines if filters are to be updated afterwards.
     * @param[suppressErr]      suppresses the error generated in case a non present [flowId] is passed as param.
     */
    fun resetAndRmFlow(flowId: FlowId, updateFilters: Boolean = true, suppressErr: Boolean = false) {
        filters.remove(flowId)
            ?. also {
                it.filtered.dataRate = .0
                if (updateFilters) updateFilters();
            }
            ?: let { if (!suppressErr) log.error("asked to remove a flow which is not present on this medium") }
    }

    /**
     * Resets all flows and updates [nextFilter].
     * @see[resetAndRmFlow]
     */
    fun resetAll() {
        filters.keys.toList().forEach { resetAndRmFlow(it, updateFilters = false) }
        nextFilter?.updateFilters()
    }

    /**
     * @param[unfiltered]               the unfiltered flow whose dedicated data rate is to be returned.
     * @param[totalIncomingDataRate]    the sum of the data rate of all incoming unfiltered flows.
     * @return                          the data rate that ***this***medium provides to the flow, hence the data rate of the filtered flow.
     */
    private fun dedicatedDataRateOf(unfiltered: Flow, totalIncomingDataRate: Kbps): Kbps {
        val dedicatedLinkUtilizationPercentage: Kbps =
            (unfiltered.dataRate / totalIncomingDataRate)
                .let { if (it.isNaN()) .0 else it }

        check (dedicatedLinkUtilizationPercentage in 0.0..1.0)
        {"dedicated link utilization should be between 0 and 1 but is $dedicatedLinkUtilizationPercentage"}

        return min(dedicatedLinkUtilizationPercentage * maxBW, unfiltered.dataRate)
    }


    /**
     * Holds the [unfiltered] (incoming) and
     * its resulting [filtered] (outgoing) flows.
     * @param[unfiltered]   incoming flow.
     * @param[filtered]     outgoing flow.
     */
    inner class Filter (
        val unfiltered: Flow,
        val filtered: Flow = unfiltered.copy(dataRate = .0),
    ) {
        /**
         * ID of the flow that is filtered.
         */
        val id: FlowId = unfiltered.id

        /**
         * Utility function since inner classes are not allowed to be data classes.
         */
        fun copy(flowIn: Flow = this.unfiltered, flowOut: Flow = this.filtered): Filter =
            Filter(flowIn, flowOut)
    }
}
