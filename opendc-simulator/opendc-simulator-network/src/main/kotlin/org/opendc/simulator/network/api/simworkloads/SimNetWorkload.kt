package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import org.opendc.trace.preset.BitBrains
import org.opendc.trace.table.Table
import org.opendc.trace.table.TableReader
import org.opendc.trace.table.concatWithName
import java.io.File
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

    public val startInstant: Instant = events.peek()?.deadline?.let { Instant.ofEpochMilli(it) }
        ?: Instant.ofEpochMilli(ms.MIN_VALUE)


    init {
        check(hostIds.none { it in coreIds } && INTERNET_ID !in coreIds && INTERNET_ID !in hostIds)
        { "unable to create workload, conflicting ids" }

        println("grouped: ${events.groupBy { it.deadline }.size}")
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
        if (allWorkLoadIds.any { it !in  allMappedIds})
            log.warn("not all workload ids have been mapped to physical network, " +
                "some ids were not categorizable as specific node types")
    }

    internal suspend fun execNext(controller: NetworkController) {
        events.poll()?.execIfNotPassed(controller)
            ?: log.error("unable to execute network event, no more events remaining in the workload")
    }

    internal fun hasNext(): Boolean =
        events.isNotEmpty()

    internal suspend fun execUntil(controller: NetworkController, until: ms) {
        var event: NetworkEvent?

        while ((events.peek()?.deadline ?: ms.MAX_VALUE) < until) {
            event = events.poll()
            event?.execIfNotPassed(controller)
        }
    }

    internal fun peek(): NetworkEvent = events.peek()

    public fun execOn(networkFile: File, withVirtualMapping: Boolean = true) {
        val controller = NetworkController.fromFile(networkFile)

        controller.execWorkload(this, withVirtualMapping = withVirtualMapping)

        println(controller.energyRecorder.getFmtReport())
    }

    internal fun execOn(network: Network, withVirtualMapping: Boolean = true) {
        val controller = NetworkController(network)

        controller.execWorkload(this, withVirtualMapping = withVirtualMapping)

        println(controller.energyRecorder.getFmtReport())
    }

    internal fun optimize(): SimNetWorkload {
        TODO()
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
            val deadlineRd = tblReader.addColumnReader(BitBrains.TIMESTAMP)!!

            val vmIds = mutableSetOf<Long>()
            val netEvents = mutableListOf<NetworkEvent>()
            while (tblReader.nextLine()) {
                vmIds.add(idRd.currRowValue)

                netEvents.add(
                    NetworkEvent.FlowUpdate(
                        from = idRd.currRowValue,
                        to = INTERNET_ID,
                        desiredDataRate = netTxRd.currRowValue,
                        deadline = deadlineRd.currRowValue
                    )
                )

                netEvents.add(
                    NetworkEvent.FlowUpdate(
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
