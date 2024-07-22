package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms

internal abstract class NetworkEvent: Comparable<NetworkEvent> {
    private companion object {
        private val log by logger()
        val bo = mutableListOf<ms>()
    }

    abstract val deadline: ms
    lateinit var targetFlow: NetFlow



    internal open fun involvedIds(): Set<NodeId> = setOf()
    protected abstract suspend fun NetworkController.exec()

    suspend fun NetworkController.execIfNotPassed() {
        val msSinceLastUpdate: ms = deadline - this.instantSrc.millis()
        if (msSinceLastUpdate < 0)
            return log.error("unable to execute network event, deadline is passed")

        this.advanceBy(msSinceLastUpdate)

        exec()
    }

    override fun compareTo(other: NetworkEvent): Int =
        (this.deadline - other.deadline).toInt()

    /**
     * Only one flow is allowed between 2 nodes.
     */
    data class FlowUpdate(
        override val deadline: ms,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: Kbps
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startOrUpdateFlow(
                transmitterId = from,
                destinationId = to,
                desiredDataRate = desiredDataRate
            ) ?. let { targetFlow = it }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)

        fun toFlowStart(): FlowStart =
            FlowStart(deadline = deadline, from = from, to = to, desiredDataRate = desiredDataRate)

        fun toFlowChangeRate(flowGetter: () -> NetFlow): FlowChangeRate =
            FlowChangeRate(deadline = deadline, newRate = desiredDataRate, flowGetter = flowGetter)
    }

    data class FlowChangeRate(
        override val deadline: ms,
        val newRate: Kbps,
        private val flowGetter: suspend () -> NetFlow
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            val flow = flowGetter()
            flow.setDesiredDataRate(newRate)
            targetFlow = flow
        }
    }

    data class FlowStart(
        override val deadline: ms,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: Kbps,
        val flowId: FlowId = IdDispenser.nextFlowId
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startFlow(
                transmitterId = from,
                destinationId = to,
                desiredDataRate = desiredDataRate,
                flowId = flowId
            ) ?. let { targetFlow = it }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)
    }

    data class FlowStop(
        override val deadline: ms,
        private val flowIdGetter: suspend () -> FlowId
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.stopFlow(
                flowId = flowIdGetter.invoke()
            ) ?. let { targetFlow = it }
        }
    }
}
