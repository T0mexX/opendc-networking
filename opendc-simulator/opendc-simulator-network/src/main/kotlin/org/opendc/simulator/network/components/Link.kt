package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.utils.Kbps
import kotlin.math.min

///**
// * Link that connects 2 [Node]s. Its maximum bandwidth
// * is determined by the minimum port speed between the 2 nodes.
// * @param[sender]   sender node.
// * @param[receiver] receiver node.
// */
internal class Link(
    private val senderPort: Port,
    private val receiverPort: Port,
    override val maxBW: Kbps = min(senderPort.speed, receiverPort.speed)
): FlowFilterer() {

    init {
        senderPort.linkOut = this
        receiverPort.linkIn = this
    }

//    /**
//     * Pulls a flow from [sender]-[Node] into the link.
//     * If flow with same id already exists, they are swapped.
//     * If needed, flow filters are adjusted and the resulting
//     * output flow is pushed to the [receiver]-[Node].
//     */
    fun pullFlow(flow: Flow) {
        super<FlowFilterer>.addFlow(flow, ifNew = receiverPort::pullFlow)
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
