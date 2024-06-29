package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.Kbps
import kotlin.math.min

/**
 * Link that connects 2 [Node]s. Its maximum bandwidth
 * is determined by the minimum port speed between the 2 nodes.
 * @param[sender]   sender node.
 * @param[receiver] receiver node.
 */
internal class Link(private val sender: Node, private val receiver: Node) {
    /**
     * Represent the maximum bandwidth of the link, which is determined by the minimum
     * port speed between the 2 nodes.
     */
    private val maxBandwidth: Kbps = min(sender.portSpeed, receiver.portSpeed)

    /**
     * Maps each [Flow]'s id to its [LinkFlowFilter].
     */
    private val currLinkFlows: MutableMap<FlowId, LinkFlowFilter> = HashMap()

    /**
     * Pulls a flow from [sender]-[Node] into the link.
     * If needed, flow filters are adjusted and the resulting
     * output flow is pushed to the [receiver]-[Node].
     */
    fun pullFlow(flow: Flow) {
        if (currLinkFlows.contains(flow.endToEndFlowId))
            subExistingFlow(flow)
        else pullNewFlow(flow)
    }

    /**
     * Substitutes the existing incoming flow with the
     * same id as [flow] with [flow].
     * @param[flow]  replacer flow.
     */
    private fun subExistingFlow(flow: Flow) {
        val newFlowId: FlowId = flow.endToEndFlowId
        currLinkFlows[newFlowId]?. let { linkFlowFilter ->
            // handle case flow is already present
            currLinkFlows[newFlowId] = linkFlowFilter.copy(flowIn = flow)
            updateFilters()
            return
        } ?: throw IllegalArgumentException("this function should be called only if a flow with the " +
            "same id of the parameter is already present in the link")
    }

    /**
     * Pulls a new flow from [sender]-[Node]. Requires that
     * no flow with same id already exists in the link.
     * @param[newFlow]  new flow to pull.
     */
    private fun pullNewFlow(newFlow: Flow) {
        val newFlowId: FlowId = newFlow.endToEndFlowId
        require(!currLinkFlows.containsKey(newFlowId))
        { "this function should be called only if a flow with the " +
            "same id of the parameter is not already present in the link" }

        val outputFlow = Flow(
            sender = sender,
            dataRate = 0.0, // it's going to be updated
            endToEndFlowId = newFlowId,
            finalDestinationId = newFlow.finalDestinationId
        )
        val newLinkFlowFilter = LinkFlowFilter(
            flowIn = newFlow,
            flowOut = outputFlow,
        )
        currLinkFlows[newFlowId] = newLinkFlowFilter
        updateFilters()
        receiver.pushNewFlow(newLinkFlowFilter.flowOut)
    }

    /**
     * Updates [LinkFlowFilter]s based on the incoming flows data rates, so that
     * the maximum bandwidth is not exceeded and each flow has a throughput
     * which is proportional to its incoming data rate, simulating a link bottleneck.
     */
    private fun updateFilters() {
        val totalIncomingDataRate: Kbps = currLinkFlows.values.sumOf { it.flowIn.dataRate }
        currLinkFlows.values.forEach { linkFlowFilter ->
            val dedicatedLinkUtilizationPercentage: Kbps = linkFlowFilter.flowIn.dataRate / totalIncomingDataRate
            assert(dedicatedLinkUtilizationPercentage in 0.0..1.0)
            val dedicatedLinkUtilization: Kbps = min(dedicatedLinkUtilizationPercentage * maxBandwidth, linkFlowFilter.flowIn.dataRate)
            linkFlowFilter.flowOut.dataRate = dedicatedLinkUtilization
        }
    }

    /**
     * Returns the [Node] on the opposite side of the [Link].
     * @param[requesterNode]    node of which the opposite is to be returned.
     */
    @Throws (IllegalArgumentException::class) // For java compatibility
    fun opposite(requesterNode: Node): Node {
        return when {
            requesterNode === sender -> receiver
            requesterNode === receiver -> sender
            else -> throw IllegalArgumentException("Non link node $requesterNode requesting its opposite. " +
                "This link is between $sender and $receiver")
        }
    }

    /**
     * This filter directly connects an input flow to an output flow.
     * It listens to incoming flow data rate changes and makes the link update the outgoing data rates accordingly.
     * The [Link] handles the filters so that the maximum bandwidth is not exceeded and each
     * flow has a throughput which is proportional to its incoming data rate, simulating a link bottleneck.
     * @param[flowIn]       incoming flow.
     * @param[flowOut]      outgoing flow after filtered.
     */
    inner class LinkFlowFilter(
        val flowIn: Flow,
        val flowOut: Flow,
    ) {
        private val dataRateOnChangeHandler = OnChangeHandler<Flow, Kbps> { _, _, _ ->
            this@Link.updateFilters()
        }

        init {
            flowIn.addDataRateObsChangeHandler(this.dataRateOnChangeHandler)
        }

        fun copy(flowIn: Flow = this.flowIn, flowOut: Flow = this.flowOut): LinkFlowFilter =
            LinkFlowFilter(flowIn, flowOut)
    }
}
