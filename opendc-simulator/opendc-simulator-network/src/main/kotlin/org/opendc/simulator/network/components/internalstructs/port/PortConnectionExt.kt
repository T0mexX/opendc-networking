package org.opendc.simulator.network.components.internalstructs.port

import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.components.link.SimplexLink
import org.opendc.simulator.network.flow.RateUpdt.Companion.toRateUpdt
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import kotlin.math.min


private val log by Unit.logger("PortConnectionExt")


internal suspend fun Port.connect(other: Port, duplex: Boolean = true, linkBW: Kbps? = null) =
    this.connect(other = other, duplex = duplex, linkBW = linkBW, notifyOther = true)

private suspend fun Port.connect(
    other: Port,
    duplex: Boolean,
    linkBW: Kbps?,
    notifyOther: Boolean
) {
    require(this.sendLink == null)
    { "unable to connect ports $this and $other. $this is already connected" }

    val computedLinkBW: Kbps = linkBW ?: (min(this.maxSpeed, other.maxSpeed))

    val thisToOther = SimplexLink(senderP = this, receiverP = other, linkBW = computedLinkBW)
    this.sendLink = thisToOther
    other.receiveLink = thisToOther

    if (duplex && notifyOther) {
        runCatching { other.connect(other = this, duplex = true, linkBW = computedLinkBW, notifyOther = false) }
            .also { if (it.isFailure) {
                // disconnects the established 1 way link before rethrowing exception
                this.disconnect()
                throw it.exceptionOrNull()!!
            }  }
    }
}

internal suspend fun Port.disconnect() {
    if (this.isConnected.not()) return log.warn("unable to disconnect port $this, port not connected")

    val update: RateUpdt? = receiveLink?.incomingRateById?.mapValues { (_, rate) -> -rate }?.toRateUpdt()

    sendLink = null
    receiveLink = null

    // should not suspend since channel has unlimited capacity
    if (update?.isNotEmpty() == true)
        update.let { nodeUpdtChl.send(it) }
}
