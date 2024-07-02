package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.components.FlowFilterer
import org.opendc.simulator.network.components.Link
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.largerThanBy
import org.opendc.simulator.network.utils.logger

/**
 * Represents a single network port. Part of a [Node].
 * @param[speed]    speed of the port (full-duplex).
 * @param[node]     the node this port is part of.
 */
internal class Port(
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
            check (util in .0..1.0001) { "measured utilization ($util) not a valid percentage" }
            return minOf(util, 1.0)
        }

    val remoteConnectedPort: Port?
        get() { return linkOut?.opposite(this) }

    /**
     * The throughput of the incoming flows.
     */
    private val throughputIn: Kbps
        get() {
            return linkIn?. let {
                val throughput: Kbps = incomingFlows.values.sumOf { it.dataRate }
                check (throughput.largerThanBy(maxNodeToNodeSpeed, deltaPerc = 0.001))
                { "measured throughputIn ($throughput) higher than possible link speed " +
                    "(${maxNodeToNodeSpeed})" }
                minOf(throughput, maxNodeToNodeSpeed)
            } ?: .0
        }

    private val maxNodeToNodeSpeed: Double
        get() { return minOf(speed, linkIn !!.maxBW, linkIn !!.opposite(this).speed) }

    /**
     * The throughput of the outgoing flows (measured by the port on the other end of the link).
     */
    private val throughputOut: Kbps
        get() { return linkOut?.opposite(this)?.throughputIn ?: .0 }

    fun throughputInOf(flowId: FlowId): Kbps =
        `in`.filteredFlows[flowId]?.dataRate ?: .0

    fun throughputOutOf(flowId: FlowId): Kbps =
        remoteConnectedPort?.throughputInOf(flowId) ?: .0

    fun disconnect(): Result {
        if (linkIn == null || linkOut == null) {
            log.error("unable to disconnect port, already disconnected")
            return FAILURE
        }

        val otherPort: Port = linkOut !! .opposite(this)

        `in`.disconnect()
        out.disconnect()

        return  SUCCESS
    }

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

        // pushing flow to one of the nodes the flow is coming from
        // if it happens after topology changes it probably still needs to adjust
        if (`in`.filteredFlows.containsKey(flowId)) {
            log.warn("unable to push flow, receiver is one of the node the flow is coming from. " +
                "This should only happen when the topology just changed.")
            return
        }

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

    fun resetAndRmFlowIn(flowId: FlowId) {
        `in`.filteredFlows[flowId]?. let {
            `in`.resetAndRmFlow(flowId)
        }
    }

    fun resetAndRmFlowOut(flowId: FlowId){
        out.filteredFlows[flowId]?. let {
            out.resetAndRmFlow(flowId)
        }
    }

    /**
     * The "in" component of the port.

     */
    private inner class PortIn(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        /**
         * Pulls [flow] from [linkIn] into the [Node].
         * Notifies the node.
         * @see[FlowFilterer]
         * @see[FlowFilterer.addOrReplaceFlow]
         *
         */
        fun pullFlow(flow: Flow) {
            linkIn?. let {
                super<FlowFilterer>.addOrReplaceFlow(flow, ifNew = {})
                super<FlowFilterer>.lastUpdatedFlows.forEach { node.notifyFlowChange(it.id) }
            } ?: log.error("pulling $flow from not connected ${this@Port}, aborting...")
        }

        override fun updateFilters() {
            super<FlowFilterer>.updateFilters()
            super<FlowFilterer>.lastUpdatedFlows.forEach { node.notifyFlowChange(it.id) }
        }

        fun disconnect() {
            linkIn = null
            val updatedFlowsIds: Collection<FlowId> = filters.map { it.key }
            super<FlowFilterer>.resetAll()
            updatedFlowsIds.forEach { node.notifyFlowChange(it) }
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
         * @see[FlowFilterer.addOrReplaceFlow]
         */
        fun pushFlow(flow: Flow) {
            linkOut?. let {
                super<FlowFilterer>.addOrReplaceFlow(flow, ifNew = linkOut!!::pullFlow)
                linkOut?.updateFilters()
            } ?: log.error("pushing $flow from not connected ${this@Port}, aborting...")
        }

        override fun updateFilters() {
            super<FlowFilterer>.updateFilters()
            linkOut?.updateFilters()
        }

        fun disconnect() {
            linkOut = null
            super<FlowFilterer>.resetAll()
        }
    }
}
