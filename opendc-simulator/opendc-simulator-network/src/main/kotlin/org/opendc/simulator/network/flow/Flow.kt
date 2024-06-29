package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.Link
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.Kbps
import kotlin.properties.Delegates

internal typealias FlowId = Int

/**
 * Represent a flow that passes through a single port.
 * It can either be a flow from [Node] to [Link] or from [Link] to [Node]
 * @param[id]           id of the [EndToEndFlow] to which this port flow belongs.
 * @param[sender]                   node from which this flow is sent.
 * @param[finalDestId]       id of the node the corresponding [EndToEndFlow] is to be routed to.
 */
internal class Flow (
    val id: FlowId,
    val sender: Node,
    val finalDestId: NodeId,
    dataRate: Kbps = .0
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

    fun copy(
        id: FlowId = this.id,
        sender: Node = this.sender,
        finalDestId: NodeId = this.finalDestId,
        dataRate: Kbps = this.dataRate
    ): Flow = Flow(id, sender, finalDestId, dataRate)
}
