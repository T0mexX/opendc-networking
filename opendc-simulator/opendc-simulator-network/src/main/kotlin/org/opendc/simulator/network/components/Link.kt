package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.utils.Kbps
import kotlin.math.min

/**
 * Unidirectional link that connects 2 [Port]s. Its maximum bandwidth (if not provided)
 * is determined by the minimum speed between the 2 ports.
 * @param[senderPort]   sender node's port.
 * @param[receiverPort] receiver node's port.
 */
internal class Link(
    private val senderPort: Port,
    private val receiverPort: Port,
    override val maxBW: Kbps = min(senderPort.speed, receiverPort.speed)
): FlowFilterer() {

    init {
        senderPort.linkOut = this
        receiverPort.linkIn = this
    }

    /**
     * Pulls a flow from [senderPort]-[Node] into the link through [addOrReplaceFlow].
     * After necessary updates, the [receiverPort] is updated.
     * @see[addOrReplaceFlow]
     * @param[flow]     flow to be pulled into the link.
     */
    fun pullFlow(flow: Flow) {
        super<FlowFilterer>.addOrReplaceFlow(flow, ifNew = receiverPort::pullFlow)
        receiverPort.update()
    }

    override fun updateFilters() {
        super<FlowFilterer>.updateFilters()
        receiverPort.update()
    }

    /**
     * Returns the [Port] on the opposite side of the [Link].
     * @param[requesterPort]    port of which the opposite is to be returned.
     */
    @Throws (IllegalArgumentException::class) // For java compatibility
    fun opposite(requesterPort: Port): Port {
        return when {
            requesterPort === senderPort -> receiverPort
            requesterPort === receiverPort -> senderPort
            else -> throw IllegalArgumentException("Non link port of ${requesterPort.node} requesting its opposite. " +
                "This link is between ${senderPort.node} and ${receiverPort.node}")
        }
    }
}
