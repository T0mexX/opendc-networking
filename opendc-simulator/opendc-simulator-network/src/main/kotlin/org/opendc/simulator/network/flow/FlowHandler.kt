package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.policies.forwarding.PortSelectionPolicy
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.roundTo0ifErr

/**
 * Handles all incoming and outgoing flows of the node this handler belongs to,
 * keeping track of their demand and output rates.
 * Allows to generate and stop flows.
 *
 * Check properties and methods for more details.
 *
 * @param[ports]    ports of the node this handler belongs to.
 * Only used to provide the [availableBW].
 */
internal class FlowHandler(private val ports: Collection<Port>) {

    private companion object { val log by logger() }

    /**
     * The current total available bandwidth on the switch,
     * as the sum of the available bw of the connected active ports.
     */
    val availableBW: Kbps get() = ports.sumOf { it.sendLink?.availableBW ?: .0 }

    /**
     * [NetFlow]s whose sender is the node to which this flow handler belongs.
     * Needs to be kept updated by owner [Node] through [generateFlow] and [stopGeneratedFlow].
     * If the node is not [EndPointNode] this property shall be ignored.
     */
    val generatedFlows: Map<FlowId, NetFlow> get() = _generatedFlows
    private val _generatedFlows = mutableMapOf<FlowId, NetFlow>()

    /**
     * [NetFlow]s whose destination is the node to which this flow handler belongs.
     * Needs to be kept updated by owner [Node]. If the node is not an
     * [EndPointNode] this property shall be ignored.
     */
    val receivingFlows = mutableMapOf<FlowId, NetFlow>()

    /**
     * Keeps track of all outgoing flows in the form of [OutFlow]s.
     * It is updated internally whenever [updtFlows] is invoked.
     *
     * Entries are added if a new flow is received.
     * Entries are removed if their [OutFlow.demand] is set to 0.
     */
    val outgoingFlows: Map<FlowId, OutFlow> get() = _outgoingFlows
    private val _outgoingFlows = mutableMapOf<FlowId, OutFlow>()

    /**
     * Keeps track of those flows whose demand is not satisfied,
     * maintaining a collection of these flows ordered by output rate.
     */
    val unsatisfiedFlowsTracker = UnsatisfiedFlowsTracker()

    /**
     * Adds [newFlow] in the [generatedFlows] table. Additionally, it queues the [RateUpdt]
     * associated to the new flow automatically. The caller does **NOT** need to queue
     * an update itself. An observer on [newFlow] for changes of [NetFlow.demand],
     * is set up. This observer queues a demand update whenever a change occurs.
     */
    suspend fun Node.generateFlow(newFlow: NetFlow) {
        val updt = RateUpdt(
            _generatedFlows.putIfAbsent(newFlow.id, newFlow)
            // If flow with same id already present
            ?. let { currFlow ->
                log.error("adding generated flow whose id is already present. Replacing...")
                _generatedFlows[newFlow.id] = newFlow
                mapOf(newFlow.id to (newFlow.demand - currFlow.demand))
            // Else
            } ?: mapOf(newFlow.id to newFlow.demand)
        )

        // Sets up the handler of any data rate changes, propagating updates to other nodes
        // changes of this flow data rate can be performed through a NetworkController,
        // the NetworkInterface of this node, or through the instance of the NetFlow itself.
        newFlow.withDemandOnChangeHandler { _, old, new ->
            if (old == new) return@withDemandOnChangeHandler

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
        updtChl.send(  RateUpdt(fId, -removedFlow.demand)  )
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
        updt.forEach { (fId, dr) ->
            val deltaRate = dr.roundTo0ifErr()
            if (deltaRate == .0) return@forEach

            // if this node is the destination
            receivingFlows[fId]?.let {
                it.throughput += deltaRate
                return@forEach
            }
            // else
            _outgoingFlows.getOrPut(fId) {
                // if flow is new
                val newFlow = OutFlow(id = fId, unsFlowsTracker = unsatisfiedFlowsTracker)
                val outputPorts = with(this.portSelectionPolicy) { selectPorts(fId) }
                newFlow.setOutPorts(outputPorts)
                newFlow
            }.let {
                it.demand += deltaRate

                // if demand is 0 the entry is removed
                if (it.demand.roundTo0ifErr() == .0) {
                    _outgoingFlows.remove(it.id)
                }
            }
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
}

