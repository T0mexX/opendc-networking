package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.RateUpdt.Companion.toRateUpdt
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.ifNullZero
import org.opendc.simulator.network.units.minOf

internal class SimplexLink(
    private val senderP: Port,
    private val receiverP: Port,
    override val linkBW: DataRate = senderP.maxSpeed min receiverP.maxSpeed
): SendLink, ReceiveLink {

    override var usedBW: DataRate = DataRate.ZERO
        private set

    override val util: Double
        get() = (usedBW / maxPort2PortBW).let { check(it in .0..1.0); it }

    override var maxPort2PortBW: DataRate = minOf(linkBW, senderP.currSpeed, receiverP.currSpeed)
        private set

    private val _rateById = mutableMapOf<FlowId, DataRate>()
    override val incomingRateById: Map<FlowId, DataRate> get() = _rateById
    override val outgoingRatesById: Map<FlowId, DataRate> get() = _rateById


    private val currLinkUpdate = mutableMapOf<FlowId, DataRate>()

    override fun notifyPortSpeedChange() {
        maxPort2PortBW = minOf(senderP.currSpeed, receiverP.currSpeed, linkBW)
        TODO()
    }

    override fun Port.updtFlowRate(fId: FlowId, rqstRate: DataRate): DataRate =
        _rateById.compute(fId) { _, oldRate ->
            if (this !== senderP) throw RuntimeException()

            val wouldBeDeltaBW: DataRate = rqstRate - (oldRate.ifNullZero())
            val wouldBeUsedBW = usedBW + wouldBeDeltaBW

            // handles case max bandwidth is reached,
            // reducing the bandwidth increase to the maximum available
            val newRate: DataRate =
                if (wouldBeUsedBW > maxPort2PortBW)
                    (rqstRate - (wouldBeUsedBW - maxPort2PortBW)).roundedTo0WithEps()
                else rqstRate

            val deltaBw = (newRate - oldRate.ifNullZero()).roundedTo0WithEps()
            if (deltaBw.isZero()) return@compute oldRate.ifNullZero()

            // Updates the current link bandwidth usage
            usedBW += deltaBw

            if (deltaBw != DataRate.ZERO)
                currLinkUpdate.compute(fId) { _, oldDelta ->
                    val newFlowDelta = (deltaBw + (oldDelta.ifNullZero())).roundedTo0WithEps()
                    if (newFlowDelta.isZero()) null
                    else newFlowDelta
                }

            return@compute newRate
        }!!

    // Only called by sender which has the SendLink interface.
    override fun outgoingRateOf(fId: FlowId): DataRate =
        _rateById[fId].ifNullZero()


    // Only called by receiver which has the ReceiveLink interface.
    override fun incomingRateOf(fId: FlowId): DataRate =
        _rateById[fId].ifNullZero()

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


        // TODO: change
        receiverP.nodeUpdtChl.send(
            currLinkUpdate.filterNot { it.key in receiverP.outgoingRatesById }.toRateUpdt()
        )
        currLinkUpdate.clear()
    }
}

