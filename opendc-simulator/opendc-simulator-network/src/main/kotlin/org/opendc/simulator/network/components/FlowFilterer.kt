package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.OnChangeHandler
import kotlin.math.min

internal abstract class FlowFilterer {
    protected val filters: MutableMap<FlowId, Filter> = HashMap()
    protected abstract val maxBW: Kbps


    private fun updateFilters() {
        val totalIncomingDataRate: Kbps = filters.values.sumOf { it.unfiltered.dataRate }

        filters.values.forEach { linkFlowFilter ->
            val dedicatedLinkUtilizationPercentage: Kbps = linkFlowFilter.unfiltered.dataRate / totalIncomingDataRate
            assert(dedicatedLinkUtilizationPercentage in 0.0..1.0)
            val dedicatedLinkUtilization: Kbps = min(dedicatedLinkUtilizationPercentage * maxBW, linkFlowFilter.unfiltered.dataRate)
            linkFlowFilter.filtered.dataRate = dedicatedLinkUtilization
        }
    }

    fun addFlow(flow: Flow) {
        filters[flow.id]?. let { it.unfiltered.dataRate = flow.dataRate }
            ?: {
                val newFilter = Filter(unfiltered = flow)
                filters[flow.id] = newFilter
                updateFilters()
            }
    }


    inner class Filter (
        val unfiltered: Flow,
        val filtered: Flow = unfiltered.copy(dataRate = .0),
    ) {
        private val dataRateOnChangeHandler = OnChangeHandler<Flow, Kbps> { _, _, _ ->
            this@FlowFilterer.updateFilters()
        }

        init {
            unfiltered.addDataRateObsChangeHandler(this.dataRateOnChangeHandler)
        }

        fun copy(flowIn: Flow = this.unfiltered, flowOut: Flow = this.filtered): Filter =
            Filter(flowIn, flowOut)
    }
}
