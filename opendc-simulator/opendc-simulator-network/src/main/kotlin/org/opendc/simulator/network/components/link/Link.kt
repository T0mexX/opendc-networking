package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.units.DataRate

internal interface Link {
    val linkBW: DataRate
    val maxPort2PortBW: DataRate
    val usedBW: DataRate
    val util: Double
    val availableBW: DataRate get() = maxPort2PortBW - usedBW

    fun oppositeOf(p: Port): Port

    fun notifyPortSpeedChange()

//    companion object {
//
//        fun connectPorts(portA: Port, portB: Port, duplex: Boolean = true, linkBW: DataRate? = null) {
//            require(portA.isConnected.not() && portB.isConnected.not())
//            { "unable to connect ports $portA and $portB. One of the ports is already connected" }
//
//            val computedLinkBW: DataRate = linkBW ?: (min(portA.maxSpeed, portB.maxSpeed))
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
