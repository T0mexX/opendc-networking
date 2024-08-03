package org.opendc.simulator.network.flow

import org.jetbrains.annotations.TestOnly
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.tracker.FlowTracker
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.roundTo0withEps
import org.opendc.simulator.network.utils.withWarn

/**
 * Manages the outgoing flow with id [id] in a specific node.
 */
internal class OutFlow(
    val id: FlowId,
    private val flowTracker: FlowTracker
): Comparable<OutFlow> {
    /**
     * The sum of the outgoing data rate for flow with [id] on all ports.
     */
    private var _totRateOut: DataRate = DataRate.ZERO
        private set(value) {
            @Suppress("NAME_SHADOWING")
            val value = value.roundedTo0WithEps()
            with(flowTracker) { handlePropChange { field = value } }
        }
    val totRateOut: DataRate get() = _totRateOut

    /**
     * **Setter should only be used by [FlowHandler].**
     * Represents the data rate demand (to be forwarded)
     * in a node (the rate received / generated).
     */
    var demand: DataRate = DataRate.ZERO
        set(value) {
            @Suppress("NAME_SHADOWING")
            val value = value.roundedTo0WithEps()
            if (value.isZero()) {
                tryUpdtRate(DataRate.ZERO)
            }

            with (flowTracker) { handlePropChange { field = value } }
        }

    /**
     * Maps each port associated with an outgoing path for this flow, to its
     * outgoing data rate for this flow.
     */
    val outRatesByPort: Map<Port, DataRate> get() = _outRatesByPort
    private val _outRatesByPort = mutableMapOf<Port, DataRate>()

    /**
     * Tries to update the outgoing data rate for flow [id] to [newRate].
     * @return  the updated data rate, which can be less than or equal to the requested rate.
     */
    fun tryUpdtRate(newRate: DataRate = demand): DataRate {
        @Suppress("NAME_SHADOWING")
        val newRate = newRate.roundedTo0WithEps()
        if (newRate == totRateOut) return totRateOut

        val deltaRate = newRate - totRateOut
        if (deltaRate < DataRate.ZERO) reduceRate(targetRate = newRate)
        else tryIncreaseRate(targetRate = newRate)

        updtTotRateOut()
        verify()
        return totRateOut
    }

    /**
     * Tries to update the outgoing data rate for flow [id] on port [port] to rate [newRate].
     * @return  the updated data rate on port [port], which can be
     * less than or equal to the requested value.
     */
    fun tryUpdtPortRate(port: Port, newRate: DataRate): DataRate {
        @Suppress("NAME_SHADOWING")
        val newRate = newRate.roundedTo0WithEps()

        return _outRatesByPort.computeIfPresent(port) { _, _ ->
             port.tryUpdtRateOf(fId = id, targetRate = newRate)
        }?.also {
            updtTotRateOut()
        } ?: log.withWarn(DataRate.ZERO, "trying to update a " +
            "data rate of flow id $id through port $port," +
            " which is not among those used to output this flow")
    }

    /**
     * Sets the outgoing ports ([ports]) for this flow ([id]).
     * [totRateOut] is adjusted following the possible removal of possible outgoing ports.
     */
    fun setOutPorts(ports: Set<Port>) {
        val toRm = outRatesByPort.keys - ports
        toRm.forEach { port ->
            val res = port.tryUpdtRateOf(fId = id, targetRate = DataRate.ZERO)
            check(res.isZero())
        }

        ports.forEach { _outRatesByPort.putIfAbsent(it, DataRate.ZERO) }

        updtTotRateOut()
    }

    private fun updtTotRateOut() {
        _totRateOut = DataRate.ofKbps(outRatesByPort.values.sumOf { it.toKbps() })
    }

    private fun tryIncreaseRate(targetRate: DataRate) {
        check(targetRate >= totRateOut)
        if (outRatesByPort.isEmpty()) return
        var deltaRemaining: DataRate = targetRate - totRateOut
        val targetPerPort: DataRate = targetRate / outRatesByPort.size
        check (targetPerPort >= DataRate.ZERO)
        {"${outRatesByPort.size}"}

        // reluctant
        _outRatesByPort.replaceAll { port, rate ->
            if (rate >= targetPerPort) return@replaceAll rate
            val resultingRate = port.tryUpdtRateOf(fId = id, targetPerPort)

            deltaRemaining = (deltaRemaining - (resultingRate - rate)).roundedTo0WithEps()

            return@replaceAll resultingRate
        }

        if (deltaRemaining.isZero()) return

        // non reluctant
        _outRatesByPort.replaceAll { port, rate ->
            check(rate == port.outgoingRateOf(id))
            {"\n $rate ${port.incomingRateOf(id)}"}
            val resultingRate = port.tryUpdtRateOf(fId = id, targetRate = rate + deltaRemaining)

            deltaRemaining = (deltaRemaining - (resultingRate - rate)).roundedTo0WithEps()
            return@replaceAll resultingRate
        }
    }

    private fun reduceRate(targetRate: DataRate) {
        require(targetRate <= totRateOut)
        val deltaRate = targetRate - totRateOut
        _outRatesByPort.replaceAll { port, currRate ->
            // Each port rate is reduced proportionally to its contribution to the total.
            val portTargetRate = (currRate + deltaRate * (currRate / totRateOut)).roundedTo0WithEps()
            port.tryUpdtRateOf(fId = id, targetRate = portTargetRate)
        }
    }

    override fun compareTo(other: OutFlow): Int =
        totRateOut.compareTo(other.totRateOut)

    override fun toString(): String {// TODO: remove/change
        return "[id=$id, dem:${demand}, rateOut:${totRateOut}]"
    }

    override fun hashCode(): Int =
        id.hashCode()

    override fun equals(other: Any?): Boolean =
        this.javaClass == other?.javaClass && this === other

    @TestOnly
    internal fun verify() {
        _outRatesByPort.forEach { (port, rate) ->
            check(port.outgoingRateOf(id) approx rate)
            { "($id) portRate=${port.outgoingRateOf(id)}, tableRate=$rate" }
        }
    }

    private companion object { val log by logger() }
}
