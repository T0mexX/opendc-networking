package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.roundTo0ifErr
import org.opendc.simulator.network.utils.withWarn


internal class FlowHandler {

    private companion object { val log by logger() }

    /**
     * [NetFlow]s whose sender is the node to which this flow table belongs.
     * Needs to be kept updated by owner [Node] through [generateFlow] and [stopGeneratedFlow].
     * If the node is not [EndPointNode] this property shall be ignored.
     */
    private val _generatedFlows = mutableMapOf<FlowId, NetFlow>()
    val generatedFlows: Map<FlowId, NetFlow> get() = _generatedFlows

    /**
     * [NetFlow]s whose destination is the node to which this flow table belongs.
     * Needs to be kept updated by owner [Node]. If the node is not an
     * [EndPointNode] this property shall be ignored.
     */
    val receivingFlows = mutableMapOf<FlowId, NetFlow>()

    private val _outgoingFlows = mutableMapOf<FlowId, OutFlow>()
    val outgoingFlows: Map<FlowId, OutFlow> get() = _outgoingFlows

    /**
     * Computes a list of the outgoing flows sorted by their current data rate output.
     * Can be useful to apply certain [FairnessPolicy]s.
     */
    val outFlowsSortedByRate: List<OutFlow>
        get() = _outgoingFlows.values.toList().sortedBy { it.totRateOut }

    /**
     * Adds [newFlow] in the [generatedFlows] table. Additionally, it queues the [RateUpdt]
     * associated to the new flow automatically. The caller does **NOT** need to queue
     * an update itself and sets upd an observer on [newFlow] for changes in bandwidth demand.
     */
    suspend fun Node.generateFlow(newFlow: NetFlow) {
        val updt = RateUpdt(
            _generatedFlows.putIfAbsent(newFlow.id, newFlow)
            // If flow with same id already present
            ?. let { currFlow ->
                log.error("adding generated flow whose id is already present. Replacing...")
                _generatedFlows[newFlow.id] = newFlow
                mapOf(newFlow.id to (newFlow.desiredDataRate - currFlow.desiredDataRate))
            // Else
            } ?: mapOf(newFlow.id to newFlow.desiredDataRate)
        )

        // Sets up the handler of any data rate changes, propagating updates to other nodes
        // changes of this flow data rate can be performed through a NetworkController,
        // the NetNodeInterface of this node, or through the instance of the NetFlow itself.
        newFlow.withDesiredDataRateOnChangeHandler { _, old, new ->
            if (old == new) return@withDesiredDataRateOnChangeHandler

            if (new < 0) log.warn("unable to change generated flow with id '${newFlow.id}' " +
                "data-rate to $new, data-rate should be positive. Falling back to 0")

            updtChl.send(  RateUpdt(newFlow.id, (new - old))  )
        }

        // Update to be processed by the node runner coroutine.
        updtChl.send(updt)
    }

    suspend fun Node.stopGeneratedFlow(fId: FlowId) {
        val removedFlow = _generatedFlows.remove(fId)
            ?: let {
                log.error("unable to stop generated flow with id $fId in node $this, " +
                    "flow is not present in the generated flows table")
                return
            }

        // Update to be processed by the node runner coroutine
        updtChl.send(  RateUpdt(fId, -removedFlow.desiredDataRate)  )
    }

    /**
     * Consumes a [RateUpdt], adjusting each [OutFlow] demand.
     * If new flows are received, a new [OutFlow] entry is added.
     * If the receiver [Node] is the destination of a flow then its
     * end-to-end throughput is adjusted.
     *
     * Automatically makes use of the node [PortSelectionPolicy] for selecting
     * outgoing ports for new flows. The node [FairnessPolicy] is applied
     * afterwords to determine the outgoing data rate for each flow.
     */
    suspend fun Node.updtFlows(updt: RateUpdt) {
        updt.forEach { (fId, deltaRate) ->
            if (deltaRate == .0) return@forEach

            // if this node is the destination
            receivingFlows[fId]?.let {
                it.throughput += deltaRate
                return@forEach
            }
            // else
            _outgoingFlows.getOrPut(fId) {
                // if flow is new
                val newFlow = OutFlow(id = fId)
                val outputPorts = with(this.portSelectionPolicy) { selectPorts(fId) }
                newFlow.setOutPorts(outputPorts)
                newFlow
            }.demand += deltaRate
        }

        with (this.fairnessPolicy) { applyPolicy(updt) }
    }

    /**
     * Updates all the outgoing ports associated with possible routs for each flow.
     * Outgoing ports are selected following the [PortSelectionPolicy] associated with the receiver node.
     * Needs to be called after topology changes.
     */
    suspend fun Node.updtAllRouts() {
        outgoingFlows.values.toList().forEach {
            val selectedPorts = with(portSelectionPolicy) { selectPorts(it.id) }
            it.setOutPorts(selectedPorts)
        }
    }


    /**
     * Manages the outgoing flows with id [id].
     * Data rate adjustments are to be made through this interface.
     */
    inner class OutFlow(val id: FlowId): Comparable<OutFlow> {
        /**
         * The sum of the outgoing data rate for flow with [id] on all ports.
         */
        var totRateOut: Kbps = .0
            private set(value) {
                field = value.roundTo0ifErr(0.00001)
                check(field >= .0)
            }

        /**
         * **Setter should not be used externally.**
         */
        var demand: Kbps = .0
            set(value) {
                if (value.roundTo0ifErr(0.00001) == .0) {
                    _outgoingFlows.remove(id)
                    tryUpdtRate(.0)
                }
                else field = value
                check(field >= .0)
            }

        /**
         * Maps each port associated with an outgoing path for this flow, to its
         * outgoing data rate for this flow.
         */
        private val _outRatesByPort = mutableMapOf<Port, Kbps>()
        val outRatesByPort: Map<Port, Kbps> get() = _outRatesByPort

        /**
         * Tries to update the outgoing data rate for flow [id] to [newRate].
         * @return  the updated data rate, which can be less than or equal to the requested rate.
         */
        fun tryUpdtRate(newRate: Kbps = demand): Kbps {
            @Suppress("NAME_SHADOWING")
            val newRate = newRate.roundTo0ifErr(0.00001)

            val deltaRate = newRate - totRateOut
            if (deltaRate < 0) reduceRate(targetRate = newRate)
            else tryIncreaseRate(targetRate = newRate)

            updtTotRateOut()
            return totRateOut
        }

        /**
         * tries to update the outgoing data rate for flow [id] on port [port] to rate [newRate].
         * @return  the updated data rate on port [port], which can be
         * less than or equal to the requested value.
         */
        fun tryUpdtPortRate(port: Port, newRate: Kbps): Kbps {
            @Suppress("NAME_SHADOWING")
            val newRate = newRate.roundTo0ifErr(0.00001)

            return _outRatesByPort.computeIfPresent(port) { _, rate ->
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
            totRateOut = outRatesByPort.values.sum()
        }

        private fun tryIncreaseRate(targetRate: Kbps) {
            check(targetRate >= totRateOut)
            var deltaRemaining: Kbps = targetRate - totRateOut
            val targetPerPort: Kbps = targetRate / outRatesByPort.size

            // reluctant
            _outRatesByPort.replaceAll { port, rate ->
                if (rate >= targetPerPort) return@replaceAll rate
                val resultingRate = port.tryUpdtRateOf(fId = id, targetPerPort)

                deltaRemaining -= (resultingRate - rate).roundTo0ifErr()

                return@replaceAll resultingRate
            }

            if (deltaRemaining == .0) { totRateOut = targetRate; return }

            // non reluctant
            _outRatesByPort.replaceAll { port, rate ->
                check(rate == port.outgoingRateOf(id))
                {"\n $rate ${port.incomingRateOf(id)}"}
                val resultingRate = port.tryUpdtRateOf(fId = id, targetRate = rate + deltaRemaining)

                deltaRemaining -=  (resultingRate - rate).roundTo0ifErr()
                return@replaceAll resultingRate
            }
        }

        private fun reduceRate(targetRate: Kbps) {
            require(targetRate <= totRateOut)
            val deltaRate = targetRate - totRateOut
            _outRatesByPort.replaceAll { port, currRate ->
                val portTargetRate = currRate + deltaRate * (currRate / totRateOut)
                port.tryUpdtRateOf(fId = id, targetRate = portTargetRate)
            }
        }

        override fun compareTo(other: OutFlow): Int =
            totRateOut.compareTo(other.totRateOut)
    }
}

