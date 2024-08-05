package org.opendc.simulator.network.flow.tracker

import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.units.Power
import java.time.Instant

public class NodeSnapshot internal constructor(
    internal val node: Node,
    public val instant: Instant,
    public val numOfIncomingFlows: Int,
    public val numOfOutgoingFlows: Int,
    public val numOfGeneratedFlows: Int,
    public val numOfConsumedFlows: Int,
    public val minNodeThroughputPerc: Double?,
    public val maxNodeThroughputPerc: Double?,
    public val avrgNodeThroughputPerc: Double?,
    public val totNodeThroughputPerc: Double?,
    public val currPowerUse: Power
) {
    public val nodeId: NodeId = node.id

    public fun fmt(flags: Int = ALL): String {
        val colWidth = 25

        val firstLine = buildString {
            appendLine()
            append("| " + "node".padEnd(colWidth))
            flags.withFlagSet(INSTANT) { append("instant".padEnd(30)) }
            flags.withFlagSet(NUM_FLOWS_IN) { append("flows in".padEnd(colWidth)) }
            flags.withFlagSet(NUM_FLOWS_OUT) { append("flows out".padEnd(colWidth)) }
            flags.withFlagSet(NUM_GEN_FLOWS) { append("generated flows".padEnd(colWidth)) }
            flags.withFlagSet(NUM_CONS_FLOWS) { append("consumed flows".padEnd(colWidth)) }
            flags.withFlagSet(MIN_THROUGHPUT_PERC) { append("min throughput %".padEnd(colWidth)) }
            flags.withFlagSet(MAX_THROUGHPUT_PERC) { append("max throughput %".padEnd(colWidth)) }
            flags.withFlagSet(AVRG_THROUGHPUT_PERC) { append("avrg throughput %".padEnd(colWidth)) }
            flags.withFlagSet(TOT_THROUGHPUT_PERC) { append("tot throughput %".padEnd(colWidth)) }
            flags.withFlagSet(PWR_USE) { append("current pwr use (Watts)".padEnd(colWidth)) }
            appendLine()
        }

        val secondLine = buildString {
            append("| ${node.toString().padEnd(colWidth)}")
            flags.withFlagSet(INSTANT) { append(instant.toString().padEnd(30)) }
            flags.withFlagSet(NUM_FLOWS_IN) { append(numOfIncomingFlows.toString().padEnd(colWidth)) }
            flags.withFlagSet(NUM_FLOWS_OUT) { append(numOfOutgoingFlows.toString().padEnd(colWidth)) }
            flags.withFlagSet(NUM_GEN_FLOWS) { append(numOfGeneratedFlows.toString().padEnd(colWidth)) }
            flags.withFlagSet(NUM_CONS_FLOWS) { append(numOfConsumedFlows.toString().padEnd(colWidth)) }
            flags.withFlagSet(MIN_THROUGHPUT_PERC) { append(String.format("%.5f", minNodeThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(MAX_THROUGHPUT_PERC) { append(String.format("%.5f", maxNodeThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(AVRG_THROUGHPUT_PERC) { append(String.format("%.5f", avrgNodeThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(TOT_THROUGHPUT_PERC) { append(String.format("%.5f", totNodeThroughputPerc).padEnd(colWidth)) }
            flags.withFlagSet(PWR_USE) { append(String.format("%.5f", currPowerUse.toWatts()).padEnd(colWidth)) }
        }

        return firstLine + secondLine
    }

    public companion object {
        public const val INSTANT: Int = 1
        public const val NUM_FLOWS_IN: Int = 1 shl 1
        public const val NUM_FLOWS_OUT: Int = 1 shl 2
        public const val NUM_GEN_FLOWS: Int = 1 shl 3
        public const val NUM_CONS_FLOWS: Int = 1 shl 4
        public const val MIN_THROUGHPUT_PERC: Int = 1 shl 5
        public const val MAX_THROUGHPUT_PERC: Int = 1 shl 6
        public const val AVRG_THROUGHPUT_PERC: Int = 1 shl 7
        public const val TOT_THROUGHPUT_PERC: Int = 1 shl 8
        public const val PWR_USE: Int = 1 shl 9
        public const val ALL: Int = -1

        private inline fun Int.withFlagSet(flag: Int, block: () -> Unit) {
            if (this and flag != 0)
                block()
        }

        internal fun Node.snapshot(instant: Instant, withStableNetwork: Network? = null): NodeSnapshot {
            withStableNetwork?.let {
                runBlocking {
                    it.awaitStability()
                    it.validator.shouldBeStableWhile { snapshot(instant = instant) }
                }
            }

            val fh: FlowHandler = this.flowHandler
            val flows: List<OutFlow> = fh.nodeFlowTracker[AllByUnsatisfaction]

            return NodeSnapshot(
                node = this,
                instant = instant,
                numOfIncomingFlows = fh.consumedFlows.size + fh.outgoingFlows.size - fh.generatedFlows.size,
                numOfOutgoingFlows = fh.outgoingFlows.size,
                numOfGeneratedFlows = fh.generatedFlows.size,
                numOfConsumedFlows = fh.consumedFlows.size,
                minNodeThroughputPerc = flows.getOrNull(0)?.throughPutPerc(),
                maxNodeThroughputPerc = flows.lastOrNull()?.throughPutPerc(),
                avrgNodeThroughputPerc = flows.sumOf { it.throughPutPerc() } / flows.size,
                totNodeThroughputPerc = flows.sumOf { it.totRateOut.toKbps() } / flows.sumOf { it.demand.toKbps() },
                currPowerUse = (this as? EnergyConsumer<*>)?.enMonitor?.currConsumpt ?: Power.ZERO
            )
        }
    }
}

private fun OutFlow.throughPutPerc(): Double =
    (this.totRateOut / this.demand).let {
        check(it in .0..1.0)
        it
    }
