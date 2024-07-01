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
    val link: Link? = null,
) {
    companion object { private val log by logger() }
    private val `in` = PortIn()
    private val out = PortOut()
    val incomingFlows: Map<FlowId, Flow>
        get() { return `in`.filteredFlows }
    val outgoingFlows: Map<FlowId, Flow>
        get() { return out.filteredFlows }


    fun pushFlowIntoLink(flowId: FlowId, finalDestId: NodeId, dataRate: Kbps) {
        link?. let {
            out.pushFlow(Flow(
                id = flowId,
                finalDestId = finalDestId,
                sender = node,
                receiver = link.opposite(this).node
            ))
        } ?: log.error("pushing flow (id=${flowId} from not connected port $this, aborting...")
    }

    fun pushFlowIntoLink(flow: Flow) = out::pushFlow



    inner class PortIn(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        fun pullFlow(flow: Flow) {
            link?. let {
                super<FlowFilterer>.addFlow(flow)
                // TODO: add comms with node
            } ?: log.error("pulling $flow from not connected ${this@Port}, aborting...")
        }
    }

    inner class PortOut(): FlowFilterer() {
        override val maxBW: Kbps = this@Port.speed

        fun pushFlow(flow: Flow) {
            link?. let {
                super<FlowFilterer>.addFlow(flow)
                link.pullFlow(filters[flow.id]?.filtered !!)
            } ?: log.error("pushing $flow from not connected ${this@Port}, aborting...")
        }
    }

}
