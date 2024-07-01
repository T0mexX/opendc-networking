package org.opendc.simulator.network.components

import com.github.ajalt.clikt.completion.CompletionCandidates
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.OnChangeHandler
import kotlin.math.min

internal abstract class FlowFilterer {
    protected val filters: MutableMap<FlowId, Filter> = HashMap()
    abstract val maxBW: Kbps
    val filteredFlows: Map<FlowId, Flow>
        get() { return filters.mapValues { it.value.filtered } }
    var lastUpdatedFlows: List<Flow> = listOf()


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

    fun addFlow(flow: Flow, ifNew: (Flow) -> Unit = {}) {
        filters[flow.id]?. also {
            it.unfiltered.dataRate = flow.dataRate
            updateFilters()
        } ?: let {
            val newFilter = Filter(unfiltered = flow)
            filters[flow.id] = newFilter
            updateFilters()
            ifNew(newFilter.filtered)
        }
    }


    inner class Filter (
        val unfiltered: Flow,
        val filtered: Flow = unfiltered.copy(dataRate = .0),
    ) {
        val id: FlowId = unfiltered.id

        // TODO: add option to enable auto updates (with worse performance)
//        private val dataRateOnChangeHandler = OnChangeHandler<Flow, Kbps> { _, _, _ ->
//            this@FlowFilterer.updateFilters()
//        }

//        init {
//            unfiltered.addDataRateObsChangeHandler(this.dataRateOnChangeHandler)
//        }

        fun copy(flowIn: Flow = this.unfiltered, flowOut: Flow = this.filtered): Filter =
            Filter(flowIn, flowOut)
    }
}
