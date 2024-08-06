package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.api.NodeSnapshot.Companion.AVRG_PWR_USE
import org.opendc.simulator.network.api.NodeSnapshot.Companion.CURR_PWR_USE
import org.opendc.simulator.network.api.NodeSnapshot.Companion.HDR
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.export.Exportable
import org.opendc.simulator.network.units.Energy
import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.utils.ifNanThen
import java.time.Instant

public data class NetworkSnapshot internal constructor(
    public val instant: Instant,
    public val numNodes: Int,
    public val numHostNodes: Int,
    public val claimedHostNodes: Int,
    public val numCoreSwitches: Int,
    public val numActiveFlows: Int,
    public val totThroughputPerc: Double,
    public val avrgThroughputPerc: Double,
    public val currPwrUse: Power,
    public val avrgPwrUseOverTime: Power,
    public val totEnergyConsumed: Energy
): Exportable<NetworkSnapshot> {
    public fun fmt(flags: Int = ALL): String {

        val headersLine = flags.withFlagSet(HDR, dflt = "") { fmtHdr(flags) }

        val secondLine = buildString {
            flags.withFlagSet(INSTANT) { append("| ${instant.toString().padEnd(30)}") }
            flags.withFlagSet(NODES) { append(numNodes.toString().padEnd(COL_WIDTH)) }
            flags.withFlagSet(HOST_NODES) { append("${numHostNodes}(${claimedHostNodes})".padEnd(COL_WIDTH)) }
            flags.withFlagSet(CORE_SWITCHES) { append(numCoreSwitches.toString().padEnd(COL_WIDTH)) }
            flags.withFlagSet(FLOWS) { append(numActiveFlows.toString().padEnd(COL_WIDTH)) }
            flags.withFlagSet(TOT_THROUGHPUT) { append(String.format("%.5f", totThroughputPerc).padEnd(COL_WIDTH)) }
            flags.withFlagSet(AVRG_THROUGHPUT) { append(String.format("%.5f", avrgThroughputPerc).padEnd(COL_WIDTH)) }
            flags.withFlagSet(ENERGY) { append(String.format("%.5f", totEnergyConsumed.toKWh()).padEnd(COL_WIDTH)) }
        }

        return headersLine + secondLine
    }

    public companion object {
        private const val COL_WIDTH = 27

        public const val INSTANT: Int = 1
        public const val NODES: Int = 1 shl 1
        public const val HOST_NODES: Int = 1 shl 2
        public const val CORE_SWITCHES: Int = 1 shl 3
        public const val FLOWS: Int = 1 shl 4
        public const val TOT_THROUGHPUT: Int = 1 shl 5
        public const val ENERGY: Int = 1 shl 6
        public const val AVRG_THROUGHPUT: Int = 1 shl 7
        public const val ALL: Int = -1

        private inline fun <T> Int.withFlagSet(flag: Int, dflt: T? = null, block: () -> T): T? =
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
                totThroughputPerc = network.flowsById.values.let { fs -> fs.sumOf { it.throughput.toKbps() } / fs.sumOf { it.demand.toKbps() } },
                avrgThroughputPerc = network.flowsById.values.filterNot { it.demand.isZero() }.let { fs -> fs.sumOf { (it.throughput / it.demand) ifNanThen .0 } / fs.size },
                currPwrUse = energyRecorder.currPwrUsage,
                avrgPwrUseOverTime = energyRecorder.avrgPwrUsage,
                totEnergyConsumed = energyRecorder.totalConsumption
            )
        }

        /**
         * @return formatted [String] line containing all the headers
         * of the fields associated with the flags [flags]. The [HDR] flag is ignored.
         */
        public fun fmtHdr(flags: Int = NodeSnapshot.ALL): String =
            buildString {
                flags.withFlagSet(INSTANT) { append("| " + "instant".padEnd(30)) }
                flags.withFlagSet(NODES) { append("nodes".padEnd(COL_WIDTH)) }
                flags.withFlagSet(HOST_NODES) { append("hosts (assigned)".padEnd(COL_WIDTH)) }
                flags.withFlagSet(CORE_SWITCHES) { append("core switches".padEnd(COL_WIDTH)) }
                flags.withFlagSet(FLOWS) { append("active flows".padEnd(COL_WIDTH)) }
                flags.withFlagSet(TOT_THROUGHPUT) { append("tot throughput %".padEnd(COL_WIDTH)) }
                flags.withFlagSet(AVRG_THROUGHPUT) { append("avrg throughput %".padEnd(COL_WIDTH)) }
                flags.withFlagSet(CURR_PWR_USE) { append("curr pwr use [Watts]".padEnd(COL_WIDTH)) }
                flags.withFlagSet(AVRG_PWR_USE) { append("avrg pwr over time [Watts]".padEnd(COL_WIDTH)) }
                flags.withFlagSet(ENERGY) { append("energy consumed (KWh)".padEnd(COL_WIDTH)) }
            }
    }
}
