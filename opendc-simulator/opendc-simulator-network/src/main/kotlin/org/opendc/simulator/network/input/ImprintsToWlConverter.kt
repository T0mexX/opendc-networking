package org.opendc.simulator.network.input

import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.api.simworkloads.NetworkEvent
import org.opendc.simulator.network.api.simworkloads.NetworkEvent.FlowChangeRate
import org.opendc.simulator.network.api.simworkloads.NetworkEvent.FlowStart
import org.opendc.simulator.network.api.simworkloads.NetworkEvent.FlowStop
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.flow.FlowId
import kotlin.system.exitProcess


internal class ImprintsToWlConverter private constructor(
    imprints: Collection<NetEventImprint>
) {
    private val imprints: Collection<NetEventImprint> = imprints.sortedBy { it.deadline }

    // Used if flow ids are defined.
    private val encounteredFlowStartsByIds = mutableMapOf<FlowId, FlowStart>()
    private val encounteredFlowEndByIds = mutableMapOf<FlowId, FlowStop>()

    // Used if flow ids are not defined.
    private val encounteredFStartByNodesInvolved = mutableMapOf<Pair<NodeId, NodeId>, FlowStart>()
    private val encounteredFEndsByNodesInvolved = mutableMapOf<Pair<NodeId, NodeId>, FlowStop>()
    private val converted: MutableList<NetworkEvent> = ArrayList(imprints.size)

    /**
     * Converts [imprints] to a [SimNetWorkload].
     */
    private fun convert(): SimNetWorkload {
        imprints.forEach { it.convert() }
        println("imprint start: ${imprints.first().deadline.toInstantFromEpoch()}")
        println("imprint end: ${imprints.last().deadline.toInstantFromEpoch()}")

        val events = converted.sorted()
        println("start: ${events.first().deadline.toInstantFromEpoch()}")
        println("end: ${events.last().deadline.toInstantFromEpoch()}")

        val hostIds = buildSet { // TODO: remove this feature from workload and just
            events.forEach { event ->
                addAll(event.involvedIds().filterNot { it == INTERNET_ID })
            }
        }

        return SimNetWorkload(
            events = events,
            hostIds = hostIds,
        )
    }

    /**
     * Converts a single [NetEventImprint] to a [NetworkEvent], having the knowledge of the previous events.
     */
    private fun NetEventImprint.convert() {
        if (flowId == null) flowIdNull()
        else flowIdNotNull()
    }

    /**
     * Converts a single [this] to a [NetworkEvent] if [this.flowId] is `null`.
     */
    private fun NetEventImprint.flowIdNull() {
        // The nodes involved in the flow
        val nodesInvolved = Pair(transmitterId, destId)

        encounteredFStartByNodesInvolved[nodesInvolved]?.let { flowStart ->
            // Flow with these nodes involved started but end not scheduled.
            flowStart.addFlowRateChange()

            duration?.let {
                SimNetWorkload.LOG.warn("duration of flow specified on flow update instead of start, flow end schedule at update.deadline + duration")
                flowStart.addFlowStop().also { encounteredFEndsByNodesInvolved[nodesInvolved] = it }
                encounteredFStartByNodesInvolved.remove(nodesInvolved)
            }

            return
        }

        encounteredFEndsByNodesInvolved[nodesInvolved]?.let { flowStop ->
            // Flow with these nodes involved started and end is scheduled.

            if (deadline < flowStop.deadline) {
                // Update before flow stops.

                check(duration == null) {
                    "unable to specify duration for this flow, its end is already scheduled"
                }

                flowStop.addFlowRateChange()
            } else {
                // Starts a new flow with same involved nodes after the previous one has ended.

                duration?.let {
                    addFlowStart()
                    flowStop.addFlowStop().also { encounteredFEndsByNodesInvolved[nodesInvolved] = it }
                } ?: let {
                    encounteredFStartByNodesInvolved[nodesInvolved] = addFlowStart()
                }
            }

            return
        }

        val flowStart = addFlowStart()

        duration?.let {
            flowStart.addFlowStop().also { encounteredFEndsByNodesInvolved[nodesInvolved] = it }
        } ?: let { encounteredFStartByNodesInvolved[nodesInvolved] = flowStart }
    }

    /**
     * Converts [this] to a [NetworkEvent] if [this.flowId] is not `null`.
     */
    private fun NetEventImprint.flowIdNotNull() {
        val flowId = flowId!!

        encounteredFlowStartsByIds[flowId]?.let { flowStart ->
            // Checks network events consistency (same flow id => same destination and transmitter).
            check(transmitterId == flowStart.from && destId == flowStart.to) {
                "Unable to read workload: 2 entries in workload have same flow id ($flowId) but different transmitter &/or destination ids"
            }

            check(duration == null) {
                "Unable to read workload: duration value should only be defined on the start of a flow, not on an update"
            }

            check(encounteredFlowEndByIds[flowId]?.deadline?.let { it >= deadline } ?: true) {
                "Unable to read workload: found data-rate update for flow ($flowId) after the flow stop event schedule"
            }

            flowStart.addFlowRateChange()
        } ?: let {
            check(destId != INTERNET_ID || transmitterId != INTERNET_ID)

            val flowStart = addFlowStart().also { encounteredFlowStartsByIds[flowId] = it }

            duration?.let {
                flowStart.addFlowStop().also { encounteredFlowEndByIds[flowId] = it }
            }
        }

    }

    context (NetEventImprint)
    private fun addFlowStart(): FlowStart =
        let {
            flowId?.let { fId ->
                FlowStart(
                    deadline = deadline,
                    from = transmitterId,
                    to = destId,
                    desiredDataRate = netTx,
                    flowId = fId
                )
            } ?: FlowStart(
                deadline = deadline,
                from = transmitterId,
                to = destId,
                desiredDataRate = netTx,
            )
        }.also { converted += it }

    context (NetEventImprint)
    private fun NetworkEvent.addFlowStop(): FlowStop =
        FlowStop(
            deadline = this@NetEventImprint.deadline + duration!!,
            targetFlowGetter = { this.targetFlow }
        ).also { converted += it }

    context (NetEventImprint)
    private fun NetworkEvent.addFlowRateChange() =
        FlowChangeRate(
            deadline = this@NetEventImprint.deadline,
            newRate = netTx,
            targetFlowGetter = { this.targetFlow }
        ).also { converted += it }

    companion object {
        fun Collection<NetEventImprint>.toWl(): SimNetWorkload = ImprintsToWlConverter(this).convert()
    }
}
