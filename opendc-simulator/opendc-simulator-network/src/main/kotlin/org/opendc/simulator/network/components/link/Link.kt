package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.utils.Kbps
import kotlin.math.min

internal interface Link {
    val linkBW: Kbps
    val maxPort2PortBW: Kbps
    val usedBW: Kbps
    val util: Double
    val availableBW: Kbps get() = maxPort2PortBW - usedBW

    fun oppositeOf(p: Port): Port

    fun notifyPortSpeedChange()

//    companion object {
//
//        fun connectPorts(portA: Port, portB: Port, duplex: Boolean = true, linkBW: Kbps? = null) {
//            require(portA.isConnected.not() && portB.isConnected.not())
//            { "unable to connect ports $portA and $portB. One of the ports is already connected" }
//
//            val computedLinkBW: Kbps = linkBW ?: (min(portA.maxSpeed, portB.maxSpeed))
//
//            val a2b = SimplexLink(senderP = portA, receiverP = portB, linkBW = computedLinkBW)
//            portA.connectLink(a2b as SendLink)
//            portB.connectLink(a2b as ReceiveLink)
//
//            if (duplex) {
//                val b2a = SimplexLink(senderP = portB, receiverP = portA, linkBW = computedLinkBW)
//                portA.connectLink(b2a as ReceiveLink)
//                portB.connectLink(b2a as SendLink)
//            }
//        }
//    }
}
