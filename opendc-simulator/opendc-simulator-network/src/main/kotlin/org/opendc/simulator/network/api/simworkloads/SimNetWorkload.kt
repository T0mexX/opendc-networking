package org.opendc.simulator.network.api.simworkloads

import kotlinx.serialization.ExperimentalSerializationApi
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.logger
import org.opendc.trace.preset.BitBrains
import org.opendc.trace.table.Table
import org.opendc.trace.table.TableReader
import org.opendc.trace.table.concatWithName
import org.opendc.simulator.network.api.simworkloads.NetworkEvent.*
import org.opendc.simulator.network.units.Ms
import org.opendc.simulator.network.utils.withErr
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

public class SimNetWorkload internal constructor(
    netEvents: List<NetworkEvent>,
    coreIds: Collection<NodeId> = listOf(),
    hostIds: Collection<NodeId> = listOf(),
    // TODO: add end-point node category
){

    private val coreIds: Set<NodeId> = coreIds.toSet()
    private val hostIds: Set<NodeId> = hostIds.toSet()



    private val events: Queue<NetworkEvent> = LinkedList(netEvents.sorted())

    public val size: Int = events.size

    public val startInstant: Instant = events.peek()?.deadline?.let { Instant.ofEpochMilli(it.msValue().toLong()) }
        ?: Instant.ofEpochMilli(Long.MIN_VALUE)

    public val endInstant: Instant = events.last()?.deadline?.let { Instant.ofEpochMilli(it.msValue().toLong()) }
        ?: Instant.ofEpochMilli(Long.MIN_VALUE)


    init {
        check(hostIds.none { it in coreIds } && INTERNET_ID !in coreIds && INTERNET_ID !in hostIds)
        { "unable to create workload, conflicting ids" }

        if (events.any { it !is NetworkEvent.FlowUpdate } && events.any { it is FlowUpdate }) {
            log.warn("A network workload should be built with either only FlowUpdates (which assume ony 1 flow between 2 nodes) " +
                "or with FlowStart, Stop, RateUpdate. A FlowUpdate will update the first flow with the same transmitter and receiver " +
                "that it finds, if there is more than 1 between nodes, undesired behaviour is to be expected.")
        }

        log.info(
            "\n" + """
                | == NETWORK WORKLOAD ===
                | start instant: $startInstant
                | end instant: $endInstant
                | duration: ${Duration.ofMillis(endInstant.toEpochMilli() - startInstant.toEpochMilli())}
                | num of network events: ${events.size}
            """.trimIndent()
        )
    }

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
            events.forEach { addAll(it.involvedIds()) }
        }

        val allMappedIds: Set<NodeId> =
            coreIds + hostIds

        // warns if any node id present in the workload has not been mapped
        if (allWorkLoadIds.any { it !in  allMappedIds && it != INTERNET_ID}) {
            log.warn("the following workloads node ids were not mapped to network node ids: [INTERNET_ID]")
        }
    }

    internal suspend fun NetworkController.execNext() {
        events.poll()?.let { with(it) { execIfNotPassed() } }
            ?: log.error("unable to execute network event, no more events remaining in the workload")
    }

    internal fun hasNext(): Boolean =
        events.isNotEmpty()

    internal suspend fun NetworkController.execUntil(until: Ms): Long {
        var consumed: Long = 0

        while ((events.peek()?.deadline ?: Ms(Long.MAX_VALUE)) <= until) {
            events.poll()?.let { with(it) { execIfNotPassed() } }
            consumed++
        }

        return consumed
    }

    internal fun peek(): NetworkEvent = events.peek()

    @OptIn(ExperimentalSerializationApi::class)
    public fun execOn(networkFile: File, withVirtualMapping: Boolean = true) {
        val controller = NetworkController.fromFile(networkFile)

        controller.execWorkload(this, withVirtualMapping = withVirtualMapping)

        println(controller.energyRecorder.getFmtReport())
    }

    internal fun execOn(network: Network, withVirtualMapping: Boolean = true) {
        val controller = NetworkController(network)

        controller.execWorkload(this, withVirtualMapping = withVirtualMapping)
    }

    internal fun optimize(): SimNetWorkload {
        val flowGetters = mutableMapOf<Pair<NodeId, NodeId>, () -> NetFlow>()

        val newEvents = events.map { e ->
            if (e is FlowUpdate) {
                val fromTo = Pair(e.from, e.to)
                if (fromTo !in flowGetters) {
                    val start = e.toFlowStart()
                    flowGetters[fromTo] = { start.targetFlow }
                    start
                } else e.toFlowChangeRate(
                    flowGetters[fromTo] ?: return log.withErr(this, "unable to optimize, unexpected error occurred")
                )
            } else return log.withErr(this, "unable to optimize, only workloads composed of FlowUpdate exclusively are optimizable")
        }

        return SimNetWorkload(newEvents, hostIds = hostIds)
    }

    public companion object {
        private val log by logger()

        public fun fromBitBrains(trace: BitBrains, vmsRange: IntRange? = null): SimNetWorkload {
            val tblReader: TableReader = vmsRange?.let {
                val vmTables = buildList {
                    vmsRange.forEach { add(trace.tablesByName[it.toString()]) }
                }.filterNotNull()

                Table.concatWithName(vmTables, "table for vms in range $vmsRange").getReader()
            } ?: trace.allVmsTable.getReader()

            val idRd = tblReader.addColumnReader(BitBrains.VM_ID, process = { it.toLong() } )!!
            val netTxRd = tblReader.addColumnReader(BitBrains.NET_TX, process = { it * 8 /* KBps to Kbps*/ } )!!
            val netRxRd = tblReader.addColumnReader(BitBrains.NET_RX, process = { it * 8 /* KBps to Kbps*/} )!!
            val deadlineRd = tblReader.addColumnReader(BitBrains.TIMESTAMP_SEC_EPOCH, process = { Ms(Instant.ofEpochSecond(it).toEpochMilli()) })!!

            val vmIds = mutableSetOf<Long>()
            val netEvents = mutableListOf<NetworkEvent>()
            while (tblReader.nextLine()) {
                vmIds.add(idRd.currRowValue)

                netEvents.add(
                    FlowUpdate(
                        from = idRd.currRowValue,
                        to = INTERNET_ID,
                        desiredDataRate = netTxRd.currRowValue,
                        deadline = deadlineRd.currRowValue
                    )
                )

                netEvents.add(
                    FlowUpdate(
                        from = INTERNET_ID,
                        to = idRd.currRowValue,
                        desiredDataRate = netRxRd.currRowValue,
                        deadline = deadlineRd.currRowValue
                    )
                )
            }

            return SimNetWorkload(netEvents, hostIds = vmIds)
        }
    }
}
