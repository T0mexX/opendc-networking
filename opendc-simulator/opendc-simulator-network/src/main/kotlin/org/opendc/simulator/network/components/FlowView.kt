package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

/**
 * Classes implementing this interface provide
 * methods to retrieve flow information.
 */
internal interface FlowView {

    /**
     * Returns the total incoming data rate corresponding
     * to [fId] (generated flows are not included).
     */
    fun totIncomingDataRateOf(fId: FlowId): Kbps

    /**
     * Returns the total outgoing data rate corresponding to [fId] (not the throughput).
     */
    fun totOutgoingDataRateOf(fId: FlowId): Kbps

//    /**
//     * Returns the outgoing throughput of the flow corresponding to [flowId].
//     */
//    fun throughputOutOf(flowId: FlowId): Kbps

    /**
     * Returns the [FlowId]s of all flows transiting
     * through ***this*** (including generated flows).
     */
    fun allTransitingFlowsIds(): Collection<FlowId>

    /**
     * Returns a formatted [String] representation of all the flows
     * that transit through ***this***, both incoming and outgoing.
     */
    fun getFmtFlows(): String {
        val sb = StringBuilder()
        sb.appendLine(
            "id".padEnd(15) +
                "in (Kbps)".padEnd(15) +
                "out (Kbps)".padEnd(15)
//                "throughput out (Kbps)"
        )
        allTransitingFlowsIds().forEach { flowId ->
            sb.appendLine(
                flowId.toString().padEnd(15) +
                    String.format("%.6f", totIncomingDataRateOf(flowId)).padEnd(15) +
                    String.format("%.6f", totOutgoingDataRateOf(flowId)).padEnd(15)
//                    String.format("%.6f", throughputOutOf(flowId)).padEnd(15)
            )
        }
        return sb.toString()
    }
}
