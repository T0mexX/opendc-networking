package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.api.NodeSnapshot.Companion.HDR
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.export.Exportable
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
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
public data class NetworkSnapshot internal constructor(
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
): Exportable<NetworkSnapshot> {

    /**
     * @param[flags]    flags representing which property
     * need to be included in the formatted string.
     * @return          the formatted string representing the snapshot information,
     * as either 1 or 2 lines with a column for each property.
     */
    public fun fmt(flags: Int = ALL): String {

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

    override fun toString(): String = "[NetworkSnapshot: timestamp=$instant]"

    public companion object {
        private const val COL_WIDTH = 27

        /**
         * The [Instant] the [NodeSnapshot] was taken.
         */
        public const val INSTANT: Int = 1

        /**
         * The number of nodes in the network at the instant the snapshot was taken.
         */
        public const val NODES: Int = 1 shl 1

        /**
         * The number of host nodes in the network at the instant the snapshot was taken.
         */
        public const val HOST_NODES: Int = 1 shl 2

        /**
         * The number of core switches in the network at the instant the snapshot was taken.
         */
        public const val CORE_SWITCHES: Int = 1 shl 3

        /**
         * The number of flows transiting through the network at the instant the snapshot is taken.
         */
        public const val FLOWS: Int = 1 shl 4

        /**
         * The total data-rate transiting through the network at the instant the snapshot was taken.
         */
        public const val TOT_TPUT: Int = 1 shl 5

        /**
         * The total throughput percentage of all the flows transiting through
         * the network as the sum of their throughput divided by the sum of their demand.
         */
        public const val TOT_TPUT_PERC: Int = 1 shl 6

        /**
         * The average throughput percentage of all the flows transiting through
         * the network at the instant the snapshot was taken.
         */
        public const val AVRG_TPUT_PERC: Int = 1 shl 7

        /**
         * The power usage at the instant the snapshot was taken.
         */
        public const val CURR_PWR_USE: Int = 1 shl 8

        /**
         * The average power usage of the network, from the instant the node
         * was started until the instant the snapshot was taken.
         */
        public const val AVRG_PWR_USE: Int = 1 shl 9

        /**
         * The energy consumed from the instant the network was
         * started until the instant the snapshot was taken.
         */
        public const val EN_CONSUMED: Int = 1 shl 10

        /**
         * Flag that adds a line to the formatted snapshot string with the fields headers.
         */
        public const val HDR: Int = 1 shl 11

        /**
         * "Flag" that includes all the other flags.
         */
        public const val ALL: Int = -1

        /**
         * "Flag" that includes all the other flags except [HDR].
         */
        public const val ALL_NO_HDR: Int = ALL and HDR.inv()

        private inline fun <T> Int.ifSet(flag: Int, dflt: T? = null, block: () -> T): T? =
            if (this and flag != 0)
                block()
            else dflt

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
                avrgTputPerc = network.flowsById.values.filterNot { it.demand.isZero() }.let { fs -> fs.sumOf { (it.throughput / it.demand) ifNanThen .0 } / fs.size },
                currPwrUse = energyRecorder.currPwrUsage,
                avrgPwrUseOverTime = energyRecorder.avrgPwrUsage,
                totEnConsumed = energyRecorder.totalConsumption
            )
        }

        /**
         * @return formatted [String] line containing all the headers
         * of the fields associated with the flags [flags]. The [HDR] flag is ignored.
         */
        public fun fmtHdr(flags: Int = NodeSnapshot.ALL): String =
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


        private fun StringBuilder.appendPad(obj: Any?, pad: Int = COL_WIDTH) {
            append(obj.toString().padEnd(pad))
        }
        private fun StringBuilder.appendPad(str: String, pad: Int = COL_WIDTH) {
            append(str.padEnd(pad))
        }
    }
}

