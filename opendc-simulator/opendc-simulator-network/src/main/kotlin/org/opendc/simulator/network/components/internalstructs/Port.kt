package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.components.FlowFilterer
import org.opendc.simulator.network.components.Link
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger

/**
 * Represents a single network port. Part of a [Node].
 * @param[speed]    speed of the port (full-duplex).
 * @param[node]     the node this port is part of.
 */
internal data class Port(
    val speed: Kbps,
    val node: Node,
) {
    companion object { private val log by logger() }

    /**
     * The unidirectional link to which this port is connected fot incoming flows.
     */
    var linkIn: Link? = null

    /**
     * The unidirectional link to which this port is connected fot outgoing flows.
     */
    var linkOut: Link? = null

    /**
     * Hidden "in" port implementing [FlowFilterer].
     */
    private val `in` = PortIn()

    /**
     * Hidden "out" port implementing [FlowFilterer].
     */
    private val out = PortOut()

    /**
     * Flows passing through [in].
     */
    val incomingFlows: Map<FlowId, Flow>
        get() { return `in`.filteredFlows }

    /**
     * Flows passing through [out].
     */
    val outgoingFlows: Map<FlowId, Flow>
        get() { return out.filteredFlows }

    /**
     * The percentage of port utilization (considering both in and out).
     */
    val utilization: Double
        get() {
            val util: Double = (throughputIn + throughputOut) / (speed * 2)
            check (util !in .0..1.0) { "measured utilization not a valid percentage" }
            return util
        }

    /**
     * The throughput of the incoming flows.
     */
    private val throughputIn: Kbps
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

    /**
     * The throughput of the outgoing flows (measured by the port on the other end of the link).
     */
    private val throughputOut: Kbps
        get() { return linkOut?.opposite(this)?.throughputIn ?: .0 }

    /**
     * Returns `true` if this port is connected to a [Link]. Else `false`.
     */
    fun isConnected(): Boolean =
            linkIn != null && linkOut != null

    fun isActive(): Boolean =
        // TODO: implement turn off feature
        isConnected()


    /**
     * Pushes a [Flow] into the [Link] this port is connected to.
     */
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

    /**
     * Pushes a [Flow] into [linkOut].
     * @see[Port.PortOut.pushFlow]
     */
    fun pushFlowIntoLink(flow: Flow) { out.pushFlow(flow) }

    /**
     * Pulls flow from [linkIn].
     * @see[Port.PortIn.pullFlow]
     */
    fun pullFlow(flow: Flow) { `in`.pullFlow(flow) }

    /**
     * Called when incoming flow have changed and
     * ***this*** needs to update its status accordingly.
     * @see[FlowFilterer.updateFilters]
     */
    fun update() {
        `in`.updateFilters()
    }

    /**
     * The "in" component of the port.
     * Separated so that its direction has its own [FlowFilterer].
     */
    private inner class PortIn(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        /**
         * Pulls [flow] from [linkIn] into the [Node].
         * Notifies the node.
         * @see[FlowFilterer]
         * @see[FlowFilterer.addFlow]
         *
         */
        fun pullFlow(flow: Flow) {
            linkIn?. let {
                super<FlowFilterer>.addFlow(flow, ifNew = {})
                super<FlowFilterer>.lastUpdatedFlows.forEach { node.notifyFlowChange(it) }
            } ?: log.error("pulling $flow from not connected ${this@Port}, aborting...")
        }
    }

    /**
     * The "out" component of the port.
     * Separated so that its direction has its own [FlowFilterer].
     */
    private inner class PortOut(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        /**
         * Pushes flow from [node] into [linkOut].
         * Updates the link.
         * @see[FlowFilterer]
         * @see[FlowFilterer.addFlow]
         */
        fun pushFlow(flow: Flow) {
            linkOut?. let {
                super<FlowFilterer>.addFlow(flow, ifNew = linkOut!!::pullFlow)
                linkOut?.updateFilters()
            } ?: log.error("pushing $flow from not connected ${this@Port}, aborting...")
        }
    }
}
