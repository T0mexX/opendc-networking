package org.opendc.simulator.network.components.internalstructs.port

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.ifNullZero
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.withErr
import kotlin.math.max
import kotlin.math.min

/**
 * TODO: add description
 *
 * @param[maxSpeed] the maximum speed of this port.
 * Some switches can dynamically adjust the port speed,
 * therefore [currSpeed] is added.
 * @param[owner]    the node to which this port belongs.
 *
 */
internal class PortImpl(
    override val maxSpeed: DataRate,
    override val owner: Node
): Port {

    /**
     * Sender interface of a link.
     */
    override var sendLink: SendLink? = null
        set(newL) {
            // ensures ReceiveLink and SendLink are never connected to different ports
            require(receiveLink == null
                    || newL == null
                    || receiveLink?.oppositeOf(this) === newL.oppositeOf(this)
            )
            field = newL
        }

    /**
     * Receiver interface of a link.
     */
    override var receiveLink: ReceiveLink? = null
        set(newL) {
            // ensures ReceiveLink and SendLink are never connected to different ports
            require(sendLink == null
                || newL == null
                || sendLink?.oppositeOf(this) === newL.oppositeOf(this)
            )
            field = newL
        }

    override val isConnected: Boolean
        get() = sendLink != null || receiveLink != null

    override val incomingRatesById: Map<FlowId, DataRate> get() =
        receiveLink?.incomingRateById ?: emptyMap()

    override val outgoingRatesById: Map<FlowId, DataRate> get() =
        sendLink?.outgoingRatesById ?: emptyMap()

    override val maxPortToPortBW: DataRate get() =  sendLink?.maxPort2PortBW.ifNullZero()

    override val nodeUpdtChl: UpdateChl = owner.updtChl

    /**
     * For simplicity, the port is assumed to be active if any flow passes through it.
     * Topology aware policies can decide to not use a specific port
     * and that port will be assumed to be turned off.
     */
    override val isActive: Boolean
        get() = (
            sendLink?.usedBW.ifNullZero() + receiveLink?.usedBW.ifNullZero()
            ) > DataRate.ZERO

    /**
     * Utilization percentage of this port.
     * Calculated as the sum of the 'in' and 'out' data-rates on the port [maxSpeed].
     * [maxSpeed] is assumed to be in one **direction**, and the port to be full duplex.
     * Hence, the max data-rate is `maxSpeed * 2`.
     */
    override val util: Double
        get() = (
            sendLink?.usedBW.ifNullZero() + receiveLink?.usedBW.ifNullZero()
            ) / (maxSpeed * 2)

    /**
     * The current speed of the port.
     * Some switches can change their port speed dynamically to save power.
     */
    override var currSpeed: DataRate = maxSpeed
        set(s)  {
            if (s != field)
                check(s in DataRate.ZERO..maxSpeed)
                field = s
                if (field != s)
                    log.warn("unable to set '$s' as speed for port $this, " +
                        "value should be between 0 and max speed (${this.maxSpeed})"
                    )
                sendLink?.notifyPortSpeedChange()
                receiveLink?.notifyPortSpeedChange()
        }

    /**
     * The port at the other end of the link if this port is connected, null otherwise.
     */
    override val otherEndPort: Port?
        get() = sendLink?.oppositeOf(this) ?: receiveLink?.oppositeOf(this)

    /**
     * The node at the other end of the link if this port is connected, null otherwise.
     */
    override val otherEndNode: Node?
        get() = sendLink?.oppositeOf(this)?.owner ?: receiveLink?.oppositeOf(this)?.owner

    override fun incomingRateOf(fId: FlowId): DataRate =
        receiveLink?.incomingRateOf(fId).ifNullZero()

    override fun outgoingRateOf(fId: FlowId): DataRate =
        sendLink?.outgoingRateOf(fId).ifNullZero()

    override fun tryUpdtRateOf(fId: FlowId, targetRate: DataRate): DataRate =
        with(sendLink) {
            this ?: return log.withErr(DataRate.ZERO, "unable to update rate on port, port does not have an outgoing connection")
            updtFlowRate(fId, targetRate)
        }

    override suspend fun notifyReceiver() {
        sendLink?.notifyReceiver()
    }

    private companion object { val log by logger() }
}

private fun Double.between(from: Double, to: Double): Double =
    max( min(this, to), from )
