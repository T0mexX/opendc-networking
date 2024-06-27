package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.utils.Kb
import org.opendc.simulator.network.utils.Kbps
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Represents an end-to-end flow, meaning the flow from one [EndPointNode] to another.
 * This end-to-end flow can be split into multiple sub-flows along the path,
 * but ultimately each sub-flow arrives at destination.
 * @param[senderId]             id of the [EndPointNode] this end-to-end flow is generated from.
 * @param[destId]               id of the [EndPointNode] this end-to-end flow is directed to.
 * @param[flowId]               id of this end-to-end flow.
 * @param[desiredDataRate]      data rate generated by the sender.
 * @param[totalDataToTransmit]  total data to be transmitted.
 */
internal data class EndToEndFlow(
    val senderId: NodeId,
    val destId: NodeId,
    val flowId: FlowId,
    val desiredDataRate: Kbps,
    val totalDataToTransmit: Kb,
) {
    /**
     * Amount of data that is still to be transmitted.
     */
    private var remDataToTransmit: Kb = totalDataToTransmit

    /**
     * The end-to-end data rate of the flow.
     */
    var currDataRate: Kbps = 0.0

    /**
     * Returns the [Flow] that the [sender] needs to send to forward in order to start the end-to-end flow.
     * @param[sender]   the [EndPointNode] that generates the end-to-end flow.
     */
    fun getInitialFlow(sender: EndPointNode): Flow {
        require(sender.id == this.senderId) { "BO" }
        return Flow(sender = sender, dataRate = this.desiredDataRate, endToEndFlowId = this.flowId, finalDestinationId = destId)
    }

    /**
     * Advances time by [timeSpan], adjusting [remDataToTransmit] accordingly.
     */
    fun advanceBy(timeSpan: Duration) {
        remDataToTransmit -= currDataRate * timeSpan.toDouble(DurationUnit.SECONDS)
        if (remDataToTransmit <= 0) {
            // TODO: implement
        }
    }
}


