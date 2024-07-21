package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.RateUpdt.Companion.toRateUpdt
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.roundTo0ifErr
import kotlin.math.min

internal class SimplexLink(
    private val senderP: Port,
    private val receiverP: Port,
    override val linkBW: Kbps = min(senderP.maxSpeed, receiverP.maxSpeed)
): SendLink, ReceiveLink {

    override var usedBW: Kbps = .0
        private set

    override val util: Double get() = usedBW / maxPort2PortBW

    override var maxPort2PortBW: Kbps = minOf(linkBW, senderP.currSpeed, receiverP.currSpeed)
        private set

    private val _rateById = mutableMapOf<FlowId, Kbps>()
    override val incomingRateById: Map<FlowId, Kbps> get() = _rateById
    override val outgoingRatesById: Map<FlowId, Kbps> get() = _rateById


    private val currLinkUpdate = mutableMapOf<FlowId, Kbps>()

    override fun notifyPortSpeedChange() {
        maxPort2PortBW = minOf(senderP.currSpeed, receiverP.currSpeed, linkBW)
    }

    override fun updtFlowRate(fId: FlowId, rqstRate: Kbps): Kbps =
//         synchronized(_rateById) {
              _rateById.compute(fId) { _, oldRate ->
                 val wouldBeDeltaBW: Kbps = rqstRate - (oldRate ?: .0)
                 val wouldBeUsedBW = usedBW + wouldBeDeltaBW

                 // handles case max bandwidth is reached,
                 // reducing the bandwidth increase to the maximum available
                 val newRate: Kbps =
                     if (wouldBeUsedBW > maxPort2PortBW)
                         (rqstRate - (wouldBeUsedBW - maxPort2PortBW)).roundTo0ifErr()
                     else rqstRate

                 val deltaBw = (newRate - (oldRate ?: .0)).roundTo0ifErr()

                 // Updates the current link bandwidth usage
                 usedBW += deltaBw

                 if (deltaBw != .0)
                     currLinkUpdate.compute(fId) { _, oldDelta ->
                         (deltaBw + (oldDelta ?: .0)).roundTo0ifErr()
                     }

                 return@compute newRate
             }!!
//         }

    // Only called by sender which has the SendLink interface.
    override fun outgoingRateOf(fId: FlowId): Kbps =
        _rateById[fId] ?: .0


    // Only called by receiver which has the ReceiveLink interface.
    override fun incomingRateOf(fId: FlowId): Kbps =
        _rateById[fId] ?: .0

    /**
     * Returns the [Port] on the opposite side of the [SimplexLink].
     * @param[p]    port of which the opposite is to be returned.
     */
    override fun oppositeOf(p: Port): Port {
        return when {
            p === senderP -> receiverP
            p === receiverP -> senderP
            else -> throw IllegalArgumentException("Non link port of ${p.owner} requesting its opposite. " +
                "This link is between ${senderP.owner} and ${receiverP.owner}")
        }
    }


    override suspend fun notifyReceiver() {
        // channel has unlimited size => should never suspend
        if (currLinkUpdate.isEmpty()) return

        receiverP.nodeUpdtChl.send(currLinkUpdate.toRateUpdt())
        currLinkUpdate.clear()
    }


}

