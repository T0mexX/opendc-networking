package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.Link
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.util.OnChangeHandler
import org.opendc.simulator.network.utils.Kbps
import kotlin.properties.Delegates

internal typealias FlowId = Int

/**
 * Represent a flow that passes through a single port.
 * It can either be a flow from [Node] to [Link] or from [Link] to [Node]
 * @param[sender]                   node from which this flow is sent.
 * @param[endToEndFlowId]           id of the [EndToEndFlow] to which this port flow belongs.
 * @param[finalDestinationId]       id of the node the corresponding [EndToEndFlow] is to be routed to.
 */
internal class Flow(
    val sender: Node,
    val endToEndFlowId: FlowId,
    val finalDestinationId: NodeId,
    dataRate: Kbps
) {
    /**
     * The current data rate of this flow.
     */
    var dataRate: Kbps by Delegates.observable(dataRate) { _, old, new ->
        dataRateOnChangeHandlers.forEach { it.handleChange(this, old, new) }
    }

    /**
     * On change functions called on observers of the data rate property when the property has changed.
     */
    private val dataRateOnChangeHandlers: MutableList<OnChangeHandler<Flow, Kbps>> = mutableListOf()

    /**
     * Adds a data rate observer.
     */
    fun addDataRateObsChangeHandler(handler: OnChangeHandler<Flow, Kbps>) {
        dataRateOnChangeHandlers.add(handler)
    }

    /**
     * Helper method to easily copy the [Flow] changing only a subset of the parameters.
     */
    fun copy(
        sender: Node = this.sender,
        endToEndFlowId: FlowId = this.endToEndFlowId,
        finalDestinationId: NodeId = this.finalDestinationId,
        dataRate: Kbps = this.dataRate
    ): Flow {
        return Flow(sender, endToEndFlowId, finalDestinationId, dataRate)
    }

    override fun toString(): String =
        "{NetFlow (eToEId=$endToEndFlowId) from $sender with dataRate=$dataRate}"
}
