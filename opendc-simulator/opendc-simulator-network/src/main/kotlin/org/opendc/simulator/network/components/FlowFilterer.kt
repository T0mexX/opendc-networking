package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import kotlin.math.min

/**
 * Filter incoming flows ([Filter.unfiltered]) so that each flow
 * gets bandwidth proportional to its unfiltered data rate (if the bandwidth is maxed out).
 */
internal abstract class FlowFilterer {

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
     */
    fun updateFilters() {
        val lastUpdatedFlows: MutableList<Flow> = mutableListOf()
        val totalIncomingDataRate: Kbps = filters.values.sumOf { it.unfiltered.dataRate }

        filters.values.forEach { filter ->
            val dedicatedLinkUtilizationPercentage: Kbps =
                (filter.unfiltered.dataRate / totalIncomingDataRate)
                    .let { if (it.isNaN()) .0 else it }

            check (dedicatedLinkUtilizationPercentage in 0.0..1.0)
            {"dedicated link utilization should be between 0 and 1 but is $dedicatedLinkUtilizationPercentage"}
            val dedicatedLinkUtilization: Kbps = min(dedicatedLinkUtilizationPercentage * maxBW, filter.unfiltered.dataRate)

            if (dedicatedLinkUtilization != filter.filtered.dataRate) {
                filter.filtered.dataRate = dedicatedLinkUtilization
                lastUpdatedFlows.add(filter.filtered)
            }
        }

        this.lastUpdatedFlows = lastUpdatedFlows
    }

    /**
     * If a flow with same id is present, it is swapped with the new one.
     * Otherwise, the new flow is added and the callback [ifNew] is called with
     * the filtered flow.
     * @param[flow]     flow to add.
     * @param[ifNew]    function to call if the flow was
     * not present already, with the filtered flow as arg.
     */
    fun addFlow(flow: Flow, ifNew: (Flow) -> Unit = {}) {
        filters[flow.id]?. also {
            filters[flow.id] = Filter(unfiltered = flow, filtered = it.filtered)
            updateFilters()
        } ?: let {
            val newFilter = Filter(unfiltered = flow)
            filters[flow.id] = newFilter
            updateFilters()
            ifNew(newFilter.filtered)
        }
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

        // TODO: add option to enable auto updates? (with worse performance)
//        private val dataRateOnChangeHandler = OnChangeHandler<Flow, Kbps> { _, _, _ ->
//            this@FlowFilterer.updateFilters()
//        }

//        init {
//            unfiltered.addDataRateObsChangeHandler(this.dataRateOnChangeHandler)
//        }

        /**
         * Utility function since inner classes are not allowed to be data classes.
         */
        fun copy(flowIn: Flow = this.unfiltered, flowOut: Flow = this.filtered): Filter =
            Filter(flowIn, flowOut)
    }
}
