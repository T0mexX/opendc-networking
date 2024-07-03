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
import org.opendc.simulator.network.utils.errAndGet
import org.opendc.simulator.network.utils.largerThanBy
import org.opendc.simulator.network.utils.logger

/**
 * Represents a single network port. Part of a [Node].
 * @param[speed]    speed of the port (full-duplex).
 * @param[node]     the node this port is part of.
 */
internal class Port(val speed: Kbps, val node: Node) {
    companion object { private val log by logger() }

    /**
     * The unidirectional link to which this port is connected fot incoming flows.
     */
    var linkIn: Link? = null

    /**
     * The unidirectional link to which this port is connected fot outgoing flows.
     */
    var linkOut: Link? = null
        set(link) { field = link; out.nextFilter = link }

    /**
     * "in" port implementing [FlowFilterer].
     */
    val `in` = PortIn()

    /**
     * "out" port implementing [FlowFilterer].
     */
    val out = PortOut()

    /**
     * Flows passing through [in] (filtered by port speed).
     */
    val incomingFlows: Map<FlowId, Flow>
        get() { return `in`.filteredFlows }

    /**
     * Flows passing through [out] (filtered by port speed).
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

    /**
     * The [Port] to which ***this*** port is connected.
     */
    val remoteConnectedPort: Port?
        get() { return linkOut?.opposite(this) }

    /**
     * Link throughput of the incoming flows (should be filtered by both ports and the link).
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

    /**
     * Link throughput of the outgoing flows (measured by the port on the other end of the link).
     */
    private val throughputOut: Kbps
        get() { return linkOut?.opposite(this)?.throughputIn ?: .0 }

    /**
     * The maximum speed achievable on this connection,
     * which is equal to the minimum between the two ports and the link speeds.
     */
    private val maxNodeToNodeSpeed: Double
        get() { return minOf(speed, linkIn !!.maxBW, linkIn !!.opposite(this).speed) }

    /**
     * @return  link throughput of a specific incoming flow corresponding to [flowId].
     */
    private fun throughputInOf(flowId: FlowId): Kbps =
        `in`.filteredFlows[flowId]?.dataRate ?: .0

    /**
     * @return  link throughput of a specific outgoing flow corresponding to [flowId].
     */
    fun throughputOutOf(flowId: FlowId): Kbps =
        remoteConnectedPort?.throughputInOf(flowId) ?: .0

    /**
     * Disconnects the port from the link.
     * @return  [Result.SUCCESS] on success, [Result.FAILURE] otherwise.
     */
    fun disconnect(): Result {
        if (linkIn == null || linkOut == null)
            return log.errAndGet("unable to disconnect port, already disconnected")

        `in`.disconnect()
        out.disconnect()

        return  SUCCESS
    }

    /**
     * @return  `true` if this port is connected to a [Link]. `false` otherwise.
     */
    fun isConnected(): Boolean =
            linkIn != null && linkOut != null

    /**
     * @return  `true` if this port is active, `false` otherwise.
     */
    fun isActive(): Boolean =
        // TODO: implement turn off feature
        isConnected()

    /**
     * Pushes a [Flow] into the [Link] this port is connected to.
     */
    fun pushFlowIntoLink(flowId: FlowId, finalDestId: NodeId, dataRate: Kbps) {

        if (`in`.filteredFlows.containsKey(flowId)) {
            log.warn("unable to push flow, receiver is one of the node the flow is coming from. " +
                "This should only happen when the topology just changed.")
            return
        }

        linkOut?. let {
            out.addOrReplaceFlow(Flow(
                id = flowId,
                finalDestId = finalDestId,
                sender = node,
                receiver = linkOut!!.opposite(this).node,
                dataRate = dataRate
            ))
        } ?: log.error("pushing flow (id=${flowId} from not connected port $this, aborting...")
    }

    /**
     * @see[FlowFilterer.resetAndRmFlow] wrapper that suppresses the error msg since this function
     * is supposed to be called when the flow is not present.
     */
    fun resetAndRmFlowOut(flowId: FlowId) { out.resetAndRmFlow(flowId, suppressErr = true) }

    /**
     * The "in" component of the port.
     */
    inner class PortIn(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        override fun addOrReplaceFlow(flow: Flow, ifNew: (Flow) -> Unit) {
            linkIn?. let {
                super<FlowFilterer>.addOrReplaceFlow(flow, ifNew = { node.notifyFlowChange(it.id) })
            } ?: log.error("pulling $flow from not connected ${this@Port}, aborting...")
        }

        override fun updateFilters(newFlowId: FlowId?) {
            super<FlowFilterer>.updateFilters(newFlowId)
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
    inner class PortOut(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        override fun addOrReplaceFlow(flow: Flow, ifNew: (Flow) -> Unit) {
            linkOut?. let {
                super<FlowFilterer>.addOrReplaceFlow(flow, ifNew = {})
            } ?: log.error("pushing $flow from not connected ${this@Port}, aborting...")
        }

        fun disconnect() {
            linkOut = null
            super<FlowFilterer>.resetAll()
        }
    }
}
