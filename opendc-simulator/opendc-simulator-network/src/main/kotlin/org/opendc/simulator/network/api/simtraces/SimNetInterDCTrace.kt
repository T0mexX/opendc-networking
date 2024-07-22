package org.opendc.simulator.network.api.simtraces
//G
//import org.opendc.simulator.network.api.simworkloads.NetworkEvent
//import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
//import org.opendc.simulator.network.components.INTERNET_ID
//import org.opendc.simulator.network.api.NodeId
//import org.opendc.simulator.network.utils.Kbps
//import org.opendc.simulator.network.utils.ms
//import org.opendc.trace.TableReader
//import org.opendc.trace.conv.resourceID
//import org.opendc.trace.conv.resourceStateDuration
//import org.opendc.trace.conv.resourceStateNetRx
//import org.opendc.trace.conv.resourceStateNetTx
//import org.opendc.trace.conv.resourceStateTimestamp
//import org.opendc.trace.simtrace.ColReader
//import org.opendc.trace.simtrace.SimTrace
//import java.time.Duration
//import java.time.Instant
//
//public class SimNetInterDCTrace private constructor(
//    private val deadlineCol: List<ms>,
//    private val netTxCol: List<Kbps>,
//    private val netRxCol: List<Kbps>,
//    private val idCol: List<NodeId>
//): SimTrace<SimNetWorkload> {
//    public companion object {
//        public fun builder(): SimTrace.Builder<SimTrace<SimNetWorkload>, SimNetWorkload> =
//            MultiNodeBuilder()
//    }
//
//    private val numOfRows: Int
//    init {
//        require(listOf(deadlineCol, netTxCol, netRxCol)
//            .all { it.size == deadlineCol.size })
//        { "unable to build sim network trace, column sizes do not match" }
//
//        numOfRows = deadlineCol.size
//    }
//
//    /**
//     * This sim trace allows only 1 flow between 2 nodes, hence we use the [NetworkEvent.FlowUpdate]
//     * to build the workload, which does not depend on previous events (flow ids).
//     *
//     * Also, this sim network trace is for those traces that do not provide information on the destination of the flows,
//     * hence inter-datacenter transmission is assumed and all flows will be to/from internet.
//     *
//     * @see[NetworkEvent.FlowUpdate]    for explanation on how the event works.
//     * @see[SimTrace.createWorkload]    for explanation on what this function does on an api level.
//     * @return                         the [SimNetWorkload] associated with this trace.
//     */
//    override fun createWorkload(): SimNetWorkload {
//
//        // this sim trace allows only 1 flow between 2 nodes, hence we use the update event
//        // to build the workload, which does not depend on previous events (flow ids).
//        // It also overrides a previous flow with same transmitter and destination, hence no need for stop event
//
//        val hostIds = mutableSetOf<NodeId>()
//        val events = buildList<NetworkEvent> {
//            (0..<numOfRows).forEach { rowIdx ->
//
//                hostIds.add(idCol[rowIdx])
//
//                // add network update event for flow to the internet
//                add(NetworkEvent.FlowUpdate(
//                    deadline = deadlineCol[rowIdx],
//                    demand = netTxCol[rowIdx],
//                    from = idCol[rowIdx],
//                    to = INTERNET_ID
//                ))
//
//                // add network update event for flow from the internet
//                add(NetworkEvent.FlowUpdate(
//                    deadline = deadlineCol[rowIdx],
//                    demand = netTxCol[rowIdx],
//                    from = INTERNET_ID,
//                    to = idCol[rowIdx]
//                ))
//            }
//        }
//
//        return SimNetWorkload(netEvents = events, hostIds = hostIds)
//    }
//
//    private operator fun plus(other: SimNetInterDCTrace): SimNetInterDCTrace =
//        SimNetInterDCTrace(
//            deadlineCol = this.deadlineCol + other.deadlineCol,
//            netTxCol = this.netTxCol + other.netTxCol,
//            netRxCol = this.netRxCol + other.netRxCol,
//            idCol = this.idCol + other.idCol
//        )
//
//
//    private class MultiNodeBuilder: SimTrace.Builder<SimTrace<SimNetWorkload>, SimNetWorkload> {
//        private val idColReader = ColReader(resourceID, ColReader.StringType) { it.toLong() }
//        val nodeWorkloadBuildersById = mutableMapOf<NodeId, SingleNodeBuilder>()
//
//        override fun parseAndAddLine(tr: TableReader) {
//            val id: Long = idColReader.processed(tr)
//            nodeWorkloadBuildersById.computeIfAbsent(id) { SingleNodeBuilder(id) }
//                .parseAndAddLine(tr)
//        }
//
//        override fun build(): SimTrace<SimNetWorkload> {
//            return nodeWorkloadBuildersById.values
//                .map { it.build() }
//                .reduce { acc, elem -> acc + elem }
//        }
//
//    }
//
//    private class SingleNodeBuilder(private val nodeId: NodeId): SimTrace.Builder<SimTrace<SimNetWorkload>, SimNetWorkload> {
//        private val deadlineColReader = ColReader(resourceStateTimestamp, ColReader.InstantType) { it.toEpochMilli() }
//        private val durationColReader = ColReader(resourceStateDuration, ColReader.DurationType) { it.toMillis() }
//        private val networkTxColReader = ColReader(resourceStateNetTx, ColReader.DoubleType) { it * 8 /* KBps to Kbps */ }
//        private val networkRxColReader = ColReader(resourceStateNetRx, ColReader.DoubleType) { it * 8 /* KBps to Kbps */ }
//
//        private val deadlineCol: MutableList<Long> = mutableListOf()
//        private val netTxCol: MutableList<Kbps> = mutableListOf()
//        private val netRxCol: MutableList<Kbps> = mutableListOf()
//        private val idCol: MutableList<NodeId> = mutableListOf()
//
//        private var prevDeadline = Long.MIN_VALUE
//
//        private fun add(deadline: Long, netTx: Kbps, netRx: Kbps) {
//            deadlineCol.add(deadline)
//            netTxCol.add(netTx)
//            netRxCol.add(netRx)
//            idCol.add(nodeId)
//        }
//
//        override fun parseAndAddLine(tr: TableReader) {
//            val deadline: ms = deadlineColReader.processed(tr)
//            val duration: ms = durationColReader.processed(tr)
//
//            val startTimeMs = deadline - duration
//            if ((startTimeMs != prevDeadline) && (prevDeadline != Long.MIN_VALUE)) {
//                // There is a gap between the previous and current fragment; fill the gap
//                add(
//                    deadline = startTimeMs,
//                    netTx = networkTxColReader.read(tr),
//                    netRx = networkRxColReader.read(tr)
//                )
//                prevDeadline = deadline
//            }
//
//            add(
//                deadline = deadline,
//                netTx = networkTxColReader.read(tr),
//                netRx = networkRxColReader.read(tr)
//            )
//        }
//
//        override fun build(): SimNetInterDCTrace =
//            SimNetInterDCTrace(
//                deadlineCol = deadlineCol,
//                netTxCol = netTxCol,
//                netRxCol = netRxCol,
//                idCol = idCol
//            )
//    }
//
//}
