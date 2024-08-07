package org.opendc.simulator.network.api.snapshots

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.HDR
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.HDR
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.export.Exportable
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.utils.Flag
import org.opendc.simulator.network.utils.Flags
import org.opendc.simulator.network.utils.ratioToPerc
import org.opendc.simulator.network.utils.ifNanThen
import java.time.Instant

/**
 * A snapshot containing information of network collected at [instant].
 *
 * @property[instant]                   The [Instant] the [NodeSnapshot] was taken.
 * @property[numNodes]                  The number of nodes in the network at the instant the snapshot is taken.
 * @property[numHostNodes]              The number of host nodes in the network at the instant the snapshot is taken.
 * @property[claimedHostNodes]          The number of host nodes whose interface was claimed at the instant the snapshot was taken.
 * @property[numCoreSwitches]           The number of core switches in the network at the instant the snapshot is taken.
 * @property[numActiveFlows]            The number of flows transiting through the network at the instant the snapshot is taken.
 * @property[totTput]                   The total throughput of the flows, as the sum of their throughput.
 * @property[totTputPerc]               The total throughput percentage of the flows, as the sum of their throughput divided by the sum of their demand.
 * @property[avrgTputPerc]              The average throughput among all flows, as the sum of the throughput percentage of each flow, divided by the number of flows.
 * @property[currPwrUse]                The power usage at the instant the snapshot was taken.
 * @property[avrgPwrUseOverTime]        The average power usage of the network, from the instant the node was started until the instant the snapshot was taken.
 * @property[totEnConsumed]             The energy consumed from the instant the network was started until the instant the snapshot was taken.
 *
 */
public class NetworkSnapshot private constructor(
    public val instant: Instant,
    public val numNodes: Int,
    public val numHostNodes: Int,
    public val claimedHostNodes: Int,
    public val numCoreSwitches: Int,
    public val numActiveFlows: Int,
    public val totTput: DataRate,
    public val totTputPerc: Double,
    public val avrgTputPerc: Double,
    public val currPwrUse: Power,
    public val avrgPwrUseOverTime: Power,
    public val totEnConsumed: Energy
): Snapshot<NetworkSnapshot>(), Exportable<NetworkSnapshot> {

    override val dfltColWidth: Int = 27

    /**
     * @param[flags]    flags representing which property
     * need to be included in the formatted string.
     * @return          the formatted string representing the snapshot information,
     * as either 1 or 2 lines with a column for each property.
     */
    override fun fmt(flags: Flags<NetworkSnapshot>): String {

        val headersLine = flags.ifSet(HDR, dflt = "") { fmtHdr(flags) }

        val secondLine = buildString {
            append("| ")
            flags.ifSet(INSTANT) { appendPad(instant, pad = 30) }
            flags.ifSet(NODES) { appendPad(numNodes) }
            flags.ifSet(HOST_NODES) { appendPad("$numHostNodes($claimedHostNodes)") }
            flags.ifSet(CORE_SWITCHES) { appendPad(numCoreSwitches) }
            flags.ifSet(FLOWS) { appendPad(numActiveFlows) }
            flags.ifSet(TOT_TPUT) { appendPad(totTput.fmtValue("%.5f")) }
            flags.ifSet(TOT_TPUT_PERC) { appendPad(totTputPerc.ratioToPerc("%.5f")) }
            flags.ifSet(AVRG_TPUT_PERC) { appendPad(avrgTputPerc.ratioToPerc("%.5f")) }
            flags.ifSet(CURR_PWR_USE) { appendPad(currPwrUse.fmtValue("%.5f")) }
            flags.ifSet(AVRG_PWR_USE) { appendPad(avrgPwrUseOverTime.fmtValue("%.5f")) }
            flags.ifSet(EN_CONSUMED) { appendPad(totEnConsumed.fmtValue("%.5f")) }
        }

        return headersLine + secondLine
    }

    /**
     * @return formatted [String] line containing all the headers
     * of the fields associated with the flags [flags]. The [HDR] flags is ignored.
     */
    override fun fmtHdr(flags: Flags<NetworkSnapshot>): String =
        buildString {
            append("| ")
            flags.ifSet(INSTANT) { appendPad("instant", pad = 30) }
            flags.ifSet(NODES) { appendPad("nodes") }
            flags.ifSet(HOST_NODES) { appendPad("hosts (assigned)") }
            flags.ifSet(CORE_SWITCHES) { appendPad("core switches") }
            flags.ifSet(FLOWS) { appendPad("active flows") }
            flags.ifSet(TOT_TPUT) { appendPad("tot throughput") }
            flags.ifSet(TOT_TPUT_PERC) { appendPad("tot throughput %") }
            flags.ifSet(AVRG_TPUT_PERC) { appendPad("avrg throughput %") }
            flags.ifSet(CURR_PWR_USE) { appendPad("curr pwr use") }
            flags.ifSet(AVRG_PWR_USE) { appendPad("avrg pwr over time") }
            flags.ifSet(EN_CONSUMED) { appendPad("energy consumed") }
            appendLine()
        }

    override fun toString(): String = "[NetworkSnapshot: timestamp=$instant]"

    public companion object {

        /**
         * The [Instant] the [NodeSnapshot] was taken.
         */
        public val INSTANT: Flag<NetworkSnapshot> = Flag(1)

        /**
         * The number of nodes in the network at the instant the snapshot was taken.
         */
        public val NODES: Flag<NetworkSnapshot> = Flag(1 shl 1)

        /**
         * The number of host nodes in the network at the instant the snapshot was taken.
         */
        public val HOST_NODES: Flag<NetworkSnapshot> = Flag(1 shl 2)

        /**
         * The number of core switches in the network at the instant the snapshot was taken.
         */
        public val CORE_SWITCHES: Flag<NetworkSnapshot> = Flag(1 shl 3)

        /**
         * The number of flows transiting through the network at the instant the snapshot is taken.
         */
        public val FLOWS: Flag<NetworkSnapshot> = Flag(1 shl 4)

        /**
         * The total data-rate transiting through the network at the instant the snapshot was taken.
         */
        public val TOT_TPUT: Flag<NetworkSnapshot> = Flag(1 shl 5)

        /**
         * The total throughput percentage of all the flows transiting through
         * the network as the sum of their throughput divided by the sum of their demand.
         */
        public val TOT_TPUT_PERC: Flag<NetworkSnapshot> = Flag(1 shl 6)

        /**
         * The average throughput percentage of all the flows transiting through
         * the network at the instant the snapshot was taken.
         */
        public val AVRG_TPUT_PERC: Flag<NetworkSnapshot> = Flag(1 shl 7)

        /**
         * The power usage at the instant the snapshot was taken.
         */
        public val CURR_PWR_USE: Flag<NetworkSnapshot> = Flag(1 shl 8)

        /**
         * The average power usage of the network, from the instant the node
         * was started until the instant the snapshot was taken.
         */
        public val AVRG_PWR_USE: Flag<NetworkSnapshot> = Flag(1 shl 9)

        /**
         * The energy consumed from the instant the network was
         * started until the instant the snapshot was taken.
         */
        public val EN_CONSUMED: Flag<NetworkSnapshot> = Flag(1 shl 10)

        /**
         * Flag that adds a line to the formatted snapshot string with the fields headers.
         */
        public val HDR: Flag<NetworkSnapshot> = Flag(1 shl 11)

        /**
         * "Flag" that includes all the other flags except [HDR].
         */
        public val ALL_NO_HDR: Flags<NetworkSnapshot> = Flags.all<NetworkSnapshot>() - HDR

        public fun NetworkController.snapshot(): NetworkSnapshot {
            val network = this.network

            runBlocking { network.awaitStability() }

            return NetworkSnapshot(
                instant = currentInstant,
                numNodes = network.nodes.size,
                numHostNodes = network.getNodesById<HostNode>().size,
                claimedHostNodes = claimedHostIds.size,
                numCoreSwitches = network.getNodesById<CoreSwitch>().size,
                numActiveFlows = network.flowsById.values.filterNot { it.demand.isZero() }.size,
                totTput = DataRate.ofKbps(network.flowsById.values.sumOf { it.throughput.toKbps() }),
                totTputPerc = network.flowsById.values.let { fs -> fs.sumOf { it.throughput.toKbps() } / fs.sumOf { it.demand.toKbps() } },
                avrgTputPerc = network.flowsById.values.filterNot { it.demand.isZero() }
                    .let { fs -> fs.sumOf { (it.throughput / it.demand) ifNanThen .0 } / fs.size },
                currPwrUse = energyRecorder.currPwrUsage,
                avrgPwrUseOverTime = energyRecorder.avrgPwrUsage,
                totEnConsumed = energyRecorder.totalConsumption
            )
        }
    }
}

