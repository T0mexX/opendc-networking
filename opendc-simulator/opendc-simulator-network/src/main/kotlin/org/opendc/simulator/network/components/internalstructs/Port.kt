package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.components.FlowFilterer
import org.opendc.simulator.network.components.Link
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger

internal data class Port(
    val speed: Kbps,
    val node: Node,
    var linkIn: Link? = null,
    var linkOut: Link? = null
) {
    companion object { private val log by logger() }

    private val `in` = PortIn()

    private val out = PortOut()

    val incomingFlows: Map<FlowId, Flow>
        get() { return `in`.filteredFlows }

    val outgoingFlows: Map<FlowId, Flow>
        get() { return out.filteredFlows }

    val utilization: Double
        get() {
            val util: Double = (throughputIn + throughputOut) / (speed * 2)
            check (util !in .0..1.0) { "measured utilization not a valid percentage" }
            return util
        }

    val throughputIn: Kbps
        get() {
            return linkIn?. let {
                val throughput: Kbps = incomingFlows.values.sumOf { it.dataRate }
                check (throughput > speed ||
                    throughput > linkIn !!.maxBW ||
                    throughput > linkIn !!.opposite(this).speed
                ) { "measured throughput higher than port speed" }
                throughput
            } ?: .0
        }

    val throughputOut: Kbps
        get() { return linkOut?.opposite(this)?.throughputIn ?: .0 }

    fun isConnected(): Boolean =
            linkIn != null && linkOut != null


    fun pushFlowIntoLink(flowId: FlowId, finalDestId: NodeId, dataRate: Kbps) {
        linkOut?. let {
            out.pushFlow(Flow(
                id = flowId,
                finalDestId = finalDestId,
                sender = node,
                receiver = linkOut!!.opposite(this).node,
                dataRate = dataRate
            ))
        } ?: log.error("pushing flow (id=${flowId} from not connected port $this, aborting...")
    }

    fun pushFlowIntoLink(flow: Flow) { out.pushFlow(flow) }

    fun pullFlow(flow: Flow) { `in`.pullFlow(flow) }

    fun update() {
        `in`.updateFilters()
    }

    private inner class PortIn(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        fun pullFlow(flow: Flow) {
            linkIn?. let {
                super<FlowFilterer>.addFlow(flow, ifNew = {})
                super<FlowFilterer>.lastUpdatedFlows.forEach { node.notifyFlowChange(it) }
            } ?: log.error("pulling $flow from not connected ${this@Port}, aborting...")
        }
    }

    private inner class PortOut(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        fun pushFlow(flow: Flow) {
            linkOut?. let {
                super<FlowFilterer>.addFlow(flow, ifNew = linkOut!!::pullFlow)
                linkOut?.updateFilters()
            } ?: log.error("pushing $flow from not connected ${this@Port}, aborting...")
        }
    }

}
