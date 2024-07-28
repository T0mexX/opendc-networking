package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.roundTo0withEps
import org.opendc.simulator.network.utils.withWarn

/**
 * Manages the outgoing flow with id [id] in a specific node.
 */
internal class OutFlow(
    val id: FlowId,
    private val unsFlowsTracker: UnsatisfiedFlowsTracker
): Comparable<OutFlow> {
    /**
     * The sum of the outgoing data rate for flow with [id] on all ports.
     */
    private var _totRateOut: Kbps = .0
        private set(value) {
            @Suppress("NAME_SHADOWING")
            val value = value.roundTo0withEps()
            with(unsFlowsTracker) { handlePropChange { field = value } }
        }
    val totRateOut: Kbps get() = _totRateOut

    /**
     * **Setter should only be used by [FlowHandler].**
     * Represents the data rate demand (to be forwarded)
     * in a node (the rate received / generated).
     */
    var demand: Kbps = .0
        set(value) {
            @Suppress("NAME_SHADOWING")
            val value = value.roundTo0withEps()
            if (value == .0) {
                tryUpdtRate(.0)
            }

            with (unsFlowsTracker) { handlePropChange { field = value } }
        }

    /**
     * Maps each port associated with an outgoing path for this flow, to its
     * outgoing data rate for this flow.
     */
    val outRatesByPort: Map<Port, Kbps> get() = _outRatesByPort
    private val _outRatesByPort = mutableMapOf<Port, Kbps>()

    /**
     * Tries to update the outgoing data rate for flow [id] to [newRate].
     * @return  the updated data rate, which can be less than or equal to the requested rate.
     */
    fun tryUpdtRate(newRate: Kbps = demand): Kbps {
        @Suppress("NAME_SHADOWING")
        val newRate = newRate.roundTo0withEps(0.00001)

        val deltaRate = newRate - totRateOut
        if (deltaRate < 0) reduceRate(targetRate = newRate)
        else tryIncreaseRate(targetRate = newRate)

        updtTotRateOut()
        return totRateOut
    }

    /**
     * Tries to update the outgoing data rate for flow [id] on port [port] to rate [newRate].
     * @return  the updated data rate on port [port], which can be
     * less than or equal to the requested value.
     */
    fun tryUpdtPortRate(port: Port, newRate: Kbps): Kbps {
        @Suppress("NAME_SHADOWING")
        val newRate = newRate.roundTo0withEps(0.00001)

        return _outRatesByPort.computeIfPresent(port) { _, _ ->
            val resultingRate = port.tryUpdtRateOf(fId = id, targetRate = newRate)
            updtTotRateOut()
            resultingRate
        } ?: log.withWarn(.0, "trying to update a data rate of flow id $id through port $port," +
            " which is not among those used to output this flow")
    }

    /**
     * Sets the outgoing ports ([ports]) for this flow ([id]).
     * [totRateOut] is adjusted following the possible removal of possible outgoing ports.
     */
    fun setOutPorts(ports: Set<Port>) {
        val toRm = outRatesByPort.keys - ports
        toRm.forEach { port ->
            val res = port.tryUpdtRateOf(fId = id, targetRate = .0)
            check(res == .0)
        }

        ports.forEach { _outRatesByPort.putIfAbsent(it, .0) }

        updtTotRateOut()
    }

    private fun updtTotRateOut() {
        _totRateOut = outRatesByPort.values.sum()
    }

    private fun tryIncreaseRate(targetRate: Kbps) {
        check(targetRate >= totRateOut)
        if (outRatesByPort.isEmpty()) return
        var deltaRemaining: Kbps = targetRate - totRateOut
        val targetPerPort: Kbps = targetRate / outRatesByPort.size
        check (targetPerPort >= .0)
        {"${outRatesByPort.size}"}

        // reluctant
        _outRatesByPort.replaceAll { port, rate ->
            if (rate >= targetPerPort) return@replaceAll rate
            val resultingRate = port.tryUpdtRateOf(fId = id, targetPerPort)

            deltaRemaining = (deltaRemaining - (resultingRate - rate)).roundTo0withEps()

            return@replaceAll resultingRate
        }

        if (deltaRemaining == .0) return

        // non reluctant
        _outRatesByPort.replaceAll { port, rate ->
            check(rate == port.outgoingRateOf(id))
            {"\n $rate ${port.incomingRateOf(id)}"}
            val resultingRate = port.tryUpdtRateOf(fId = id, targetRate = rate + deltaRemaining)

            deltaRemaining = (deltaRemaining - (resultingRate - rate)).roundTo0withEps()
            return@replaceAll resultingRate
        }
    }

    private fun reduceRate(targetRate: Kbps) {
        require(targetRate <= totRateOut)
        val deltaRate = targetRate - totRateOut
        _outRatesByPort.replaceAll { port, currRate ->
            // Each port rate is reduced proportionally to its contribution to the total.
            val portTargetRate = (currRate + deltaRate * (currRate / totRateOut)).roundTo0withEps()
            port.tryUpdtRateOf(fId = id, targetRate = portTargetRate)
        }
    }

    override fun compareTo(other: OutFlow): Int =
        totRateOut.compareTo(other.totRateOut)

    override fun toString(): String {// TODO: remove/change
        return "[dem:${demand}, rateOut:${totRateOut}]"
    }

    override fun hashCode(): Int =
        id.hashCode()

    override fun equals(other: Any?): Boolean =
        this.javaClass == other?.javaClass && this === other

    private companion object { val log by logger() }
}
