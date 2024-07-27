package org.opendc.simulator.network.api

import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.utils.Watts
import java.time.Instant

public data class NetworkSnapshot internal constructor(
    public val instant: Instant,
    public val numOfNodes: Int,
    public val numOfHostNodes: Int,
    public val assignedHostNodes: Int,
    public val numOfCoreSwitches: Int,
    public val numOfFlows: Int,
    public val avrgThroughputPerc: Double,
    public val totEnergyConsumpt: Watts
) {
    public fun fmt(flags: Int = ALL): String {
        val colWidth = 25

        val firstLine = buildString {
            appendLine()
            flags.withFlagSet(INSTANT) { append("instant".padEnd(colWidth)) }
            flags.withFlagSet(NODES) { append("nodes".padEnd(colWidth)) }
            flags.withFlagSet(HOST_NODES) { append("hosts (assigned)".padEnd(colWidth)) }
            flags.withFlagSet(CORE_SWITCHES) { append("core switches".padEnd(colWidth)) }
            flags.withFlagSet(FLOWS) { append("flows".padEnd(colWidth)) }
            flags.withFlagSet(THROUGHPUT) { append("avr throughput %".padEnd(colWidth)) }
            flags.withFlagSet(ENERGY) { append("energy consumed".padEnd(colWidth)) }
            appendLine()
        }

        val secondLine = buildString {
            flags.withFlagSet(INSTANT) { append(instant.toString().padEnd(colWidth)) }
            flags.withFlagSet(NODES) { append(numOfNodes.toString().padEnd(colWidth)) }
            flags.withFlagSet(HOST_NODES) { append("${numOfHostNodes}(${assignedHostNodes})".padEnd(colWidth)) }
            flags.withFlagSet(CORE_SWITCHES) { append(numOfCoreSwitches.toString().padEnd(colWidth)) }
            flags.withFlagSet(FLOWS) { append(numOfFlows.toString().padEnd(colWidth)) }
            flags.withFlagSet(THROUGHPUT) { append(String.format("%.5f", avrgThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(ENERGY) { append(String.format("%.5f", totEnergyConsumpt).padEnd(colWidth)) }
            appendLine()
        }

        return firstLine + secondLine
    }

    public companion object {
        public const val INSTANT: Int = 0x1
        public const val NODES: Int = 0x2
        public const val HOST_NODES: Int = 0x4
        public const val CORE_SWITCHES: Int = 0x8
        public const val FLOWS: Int = 0x16
        public const val THROUGHPUT: Int = 0x32
        public const val ENERGY: Int = 0x64
        public const val ALL: Int = -1

        private inline fun Int.withFlagSet(flag: Int, block: () -> Unit) {
            if (this and flag != 0)
                block()
        }

        public fun NetworkController.snapshot(): NetworkSnapshot {
            val network = this.network

            return NetworkSnapshot(
                instant = this.currentInstant,
                numOfNodes = network.nodes.size,
                numOfHostNodes = network.getNodesById<HostNode>().size,
                assignedHostNodes = this.claimedHostIds.size,
                numOfCoreSwitches = network.getNodesById<CoreSwitch>().size,
                numOfFlows = network.flowsById.size,
                avrgThroughputPerc = network.flowsById.values.let { fs -> fs.sumOf { it.throughput } / fs.sumOf { it.demand } },
                totEnergyConsumpt = this.energyRecorder.totalConsumption
            )
        }
    }
}
