/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.api.workload

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.isSorted
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.withErr
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

// TODO: implement serialization for workload
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
public data class SimNetWorkload private constructor(
    private val sortedEvents: List<NetworkEvent>,
    private val coreIds: Set<NodeId> = emptySet(),
    private val hostIds: Set<NodeId> = emptySet(),
    // TODO: add end-point node category
) {
    internal constructor(
        events: Collection<NetworkEvent>,
        coreIds: Collection<NodeId> = emptySet(),
        hostIds: Collection<NodeId> = emptySet(),
    ) :
        this(sortedEvents = events.sorted(), coreIds = coreIds.toSet(), hostIds = hostIds.toSet())

    init {
        check(sortedEvents.isSorted())
    }

    private val events: Queue<NetworkEvent> = LinkedList(sortedEvents)

    public val startInstant: Instant =
        events.peek()?.deadline?.toInstantFromEpoch()
            ?: Instant.ofEpochMilli(0L)

    public val endInstant: Instant =
        events.lastOrNull()?.deadline?.toInstantFromEpoch()
            ?: Instant.ofEpochMilli(0L)

    public val size: Int = sortedEvents.size

    init {
        check(
            hostIds.none { it in coreIds } && INTERNET_ID !in coreIds && INTERNET_ID !in hostIds,
        ) { "unable to create workload, conflicting ids" }

        if (events.any { it !is NetworkEvent.FlowUpdate } && events.any { it is NetworkEvent.FlowUpdate }) {
            LOG.warn(
                "A network workload should be built with either only FlowUpdates (which assume ony 1 flow between 2 nodesById) " +
                    "or with FlowStart, Stop, RateUpdate. A FlowUpdate will update the first flow with the same transmitter and receiver " +
                    "that it finds, if there is more than 1 between nodesById, undesired behaviour is to be expected.",
            )
        }
    }

    /**
     * If this method successfully completes, the controller is then able to execute ***this*** workload,
     * even if the workload node ids do not correspond to physical node ids.
     *
     * **It does not reset the controller state**:
     * - Does not reset flows
     * - Does not reset virtual mapping (should not interfere, worst case it fucks up mapping of something else)
     * - Does not reset claimed nodesById (not all nodesById might be claimable)
     * - Does not reset time (if network events deadline are passed they won't be executed
     * - Does not reset energy consumption
     *
     *
     * @param[controller]   the [NetworkController] on which to perform the mapping.
     */
    internal fun performVirtualMappingOn(controller: NetworkController) {
        // vId = virtual id
        // pId = physical id

        // map core switch ids of the workload to physical core switches of the network
        coreIds.forEach { vId ->
            checkNotNull(
                controller.claimNextCoreNode()?.nodeId?.let {
                        pId ->
                    controller.virtualMap(vId, pId)
                },
            ) { "unable to map workload to network, not enough core switches claimable in the network" }
        }

        // map host node ids of the workload to physical host nodesById of the network
        hostIds.forEach { vId ->
            checkNotNull(
                controller.claimNextHostNode()?.nodeId?.let {
                        pId ->
                    controller.virtualMap(vId, pId)
                },
            ) { "unable to map workload to network, not enough host nodesById claimable in the network" }
        }

        val allWorkLoadIds: Set<NodeId> =
            buildSet {
                events.forEach { addAll(it.involvedIds()) }
            }

        val allMappedIds: Set<NodeId> =
            coreIds + hostIds

        // warns if any node id present in the workload has not been mapped
        if (allWorkLoadIds.any { it !in allMappedIds && it != INTERNET_ID }) {
            LOG.warn("the following workloads node ids were not mapped to network node ids: [INTERNET_ID]")
        }
    }

    internal suspend fun NetworkController.execNext() {
        events.poll()?.let { with(it) { execIfNotPassed() } }
            ?: LOG.error("unable to execute network event, no more events remaining in the workload")
    }

    internal fun hasNext(): Boolean = events.isNotEmpty()

    internal suspend fun NetworkController.execUntil(until: Time): Long {
        var consumed: Long = 0

        while ((events.peek()?.deadline ?: Time.ofMillis(Long.MAX_VALUE)) <= until) {
            events.poll()?.let { with(it) { execIfNotPassed() } }
            consumed++
        }

        advanceBy(until - instantSrc.time)

        return consumed
    }

    internal fun peek(): NetworkEvent = events.peek()

    public fun fmt(): String =
        """
        | == NETWORK WORKLOAD ===
        | start instant: $startInstant
        | end instant: $endInstant
        | duration: ${Duration.ofMillis(endInstant.toEpochMilli() - startInstant.toEpochMilli())}
        | num of network events: ${events.size}
        """.trimIndent()

//    @OptIn(ExperimentalSerializationApi::class)
//    public fun execOn(
//        networkFile: File,
//        withVirtualMapping: Boolean = true,
//    ) {
//        val controller = NetworkController.fromFile(networkFile)
//
//        controller.execWorkload(this, withVirtualMapping = withVirtualMapping)
//
//        println(controller.energyRecorder.fmt())
//    }
//
//    internal fun execOn(
//        network: Network,
//        withVirtualMapping: Boolean = true,
//    ) {
//        val controller = NetworkController(network)
//
//        controller.execWorkload(this, withVirtualMapping = withVirtualMapping)
//    }

    internal fun optimize(): SimNetWorkload {
        val flowGetters = mutableMapOf<Pair<NodeId, NodeId>, () -> NetFlow>()

        val newEvents =
            events.map { e ->
                if (e is NetworkEvent.FlowUpdate) {
                    val fromTo = Pair(e.from, e.to)
                    if (fromTo !in flowGetters) {
                        val start = e.toFlowStart()
                        flowGetters[fromTo] = { start.targetFlow }
                        start
                    } else {
                        e.toFlowChangeRate(
                            flowGetters[fromTo] ?: return LOG.withErr(this, "unable to optimize, unexpected error occurred"),
                        )
                    }
                } else {
                    return LOG.withErr(this, "unable to optimize, only workloads composed of FlowUpdate exclusively are optimizable")
                }
            }

        return SimNetWorkload(newEvents, hostIds = hostIds)
    }

    public companion object {
        internal val LOG by logger()
    }
}
