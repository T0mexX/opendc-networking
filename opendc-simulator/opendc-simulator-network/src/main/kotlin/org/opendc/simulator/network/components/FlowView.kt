package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

internal interface FlowView {

    fun totIncomingDataRateOf(flowId: FlowId): Kbps

    fun totOutgoingDataRateOf(flowId: FlowId): Kbps

    fun allTransitingFlowsIds(): Collection<FlowId>

    fun getFmtFlows(): String {
        val sb = StringBuilder()
        sb.appendLine("id".padEnd(15) + "in".padEnd(15) + "out".padEnd(15))
        allTransitingFlowsIds().forEach { flowId ->
            sb.appendLine(
                flowId.toString().padEnd(15) +
                    String.format("%.6f", totIncomingDataRateOf(flowId)).padEnd(15) +
                    String.format("%.6f", totOutgoingDataRateOf(flowId)).padEnd(15)
            )
        }
        return sb.toString()
    }
}
