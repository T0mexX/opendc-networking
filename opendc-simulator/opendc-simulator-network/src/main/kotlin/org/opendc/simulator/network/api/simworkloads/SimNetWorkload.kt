package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import org.opendc.trace.simtrace.SimWorkload
import java.io.File
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

public class SimNetWorkload internal constructor(
    netEvents: List<NetworkEvent>,
    coreIds: Collection<NodeId> = listOf(),
    hostIds: Collection<NodeId> = listOf(),
    // TODO: add end-point node category
): SimWorkload {
    private companion object { private val log by logger() }

    private val coreIds: Set<NodeId> = coreIds.toSet()
    private val hostIds: Set<NodeId> = hostIds.toSet()

    init {
        check(hostIds.none { it in coreIds } && INTERNET_ID !in coreIds && INTERNET_ID !in hostIds)
        { "unable to create workload, conflicting ids" }
    }

    private val events: Queue<NetworkEvent> = LinkedList(netEvents.sorted())

    public val startInstant: Instant = events.peek()?.deadline?.let { Instant.ofEpochMilli(it) }
        ?: Instant.ofEpochMilli(ms.MIN_VALUE)

    /**
     * If this method successfully completes, the controller is then able to execute ***this*** workload,
     * even if the workload node ids do not correspond to physical node ids.
     *
     * **It does not reset the controller state**:
     * - Does not reset flows
     * - Does not reset virtual mapping (should not interfere, worst case it fucks up mapping of something else)
     * - Does not reset claimed nodes (not all nodes might be claimable)
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
            checkNotNull(controller.claimNextCoreNode()?.nodeId?.let { pId -> controller.virtualMap(vId, pId) })
            { "unable to map workload to network, not enough core switches claimable in the network" }
        }

        // map host node ids of the workload to physical host nodes of the network
        hostIds.forEach { vId ->
            checkNotNull(controller.claimNextHostNode()?.nodeId?.let { pId -> controller.virtualMap(vId, pId) })
            { "unable to map workload to network, not enough host nodes claimable in the network" }
        }

        val allWorkLoadIds: Set<NodeId> = buildSet {
            events.forEach { plus(it.involvedIds()) }
        }

        val allMappedIds: Set<NodeId> =
            coreIds + hostIds

        // warns if any node id present in the workload has not been mapped
        if (allWorkLoadIds.any { it !in  allMappedIds})
            log.warn("not all workload ids have been mapped to physical network, " +
                "some ids were not categorizable as specific node types")
    }

    internal fun execAll(controller: NetworkController) {
        execUntil(controller, ms.MAX_VALUE)
    }

    internal fun execNext(controller: NetworkController) {
        events.poll()?.execIfNotPassed(controller)
            ?: log.error("unable to execute network event, no more events remaining in the workload")
    }

    internal fun execUntil(controller: NetworkController, until: Instant) {
        execUntil(controller, until.toEpochMilli())
    }

    internal fun execUntil(controller: NetworkController, until: ms) {
        while ((events.peek()?.deadline ?: ms.MAX_VALUE) < until) {
            events.poll()?.execIfNotPassed(controller)
        }
    }

    public fun execOn(networkFile: File, withVirtualMapping: Boolean = true) {
        NetworkController.fromFile(networkFile)
            .execWorkload(this, withVirtualMapping = withVirtualMapping)
    }
}
