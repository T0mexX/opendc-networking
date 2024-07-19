package org.opendc.simulator.network.components

import org.opendc.simulator.network.utils.Kbps
import kotlin.math.min



//
///**
// * Unidirectional link that connects 2 [Port]s. Its maximum bandwidth (if not provided)
// * is determined by the minimum speed between the 2 ports.
// * @param[senderPort]   sender node's port.
// * @param[receiverPort] receiver node's port.
// */
//internal class Link(
//    private val senderPort: Port,
//    private val receiverPort: Port,
//    override val maxBW: Kbps = min(senderPort.speed, receiverPort.speed)
//): FlowFilterer() {
//
//    init {
//        senderPort.linkOut = this
//        receiverPort.linkIn = this
//        nextFilter = receiverPort.`in`
//    }
//
//    /**
//     * Returns the [Port] on the opposite side of the [Link].
//     * @param[requesterPort]    port of which the opposite is to be returned.
//     */
//    fun opposite(requesterPort: Port): Port {
//        return when {
//            requesterPort === senderPort -> receiverPort
//            requesterPort === receiverPort -> senderPort
//            else -> throw IllegalArgumentException("Non link port of ${requesterPort.node} requesting its opposite. " +
//                "This link is between ${senderPort.node} and ${receiverPort.node}")
//        }
//    }
//}
