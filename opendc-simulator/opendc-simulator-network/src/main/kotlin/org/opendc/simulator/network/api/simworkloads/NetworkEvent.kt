package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.units.Ms
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import java.time.Instant

internal abstract class NetworkEvent: Comparable<NetworkEvent> {
    private companion object {
        private val log by logger()
    }

    abstract val deadline: Ms
    lateinit var targetFlow: NetFlow



    internal open fun involvedIds(): Set<NodeId> = setOf()
    protected abstract suspend fun NetworkController.exec()

    suspend fun NetworkController.execIfNotPassed() {
        val msSinceLastUpdate: Ms = deadline - Ms(this.instantSrc.millis())
        if (msSinceLastUpdate < Ms(0))
            return log.error("unable to execute network event, " +
                "deadline is passed (deadline=${Instant.ofEpochMilli(deadline.msValue().toLong())}, " +
                "currentInstant=${instantSrc.instant()})"
            )

        this.advanceBy(msSinceLastUpdate)

        exec()
    }

    override fun compareTo(other: NetworkEvent): Int =
        this.deadline.compareTo(other.deadline)

    /**
     * Only one flow is allowed between 2 nodes.
     */
    data class FlowUpdate(
        override val deadline: Ms,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: Kbps
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startOrUpdateFlow(
                transmitterId = from,
                destinationId = to,
                demand = desiredDataRate
            ) ?. let { targetFlow = it }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)

        fun toFlowStart(): FlowStart =
            FlowStart(deadline = deadline, from = from, to = to, desiredDataRate = desiredDataRate)

        fun toFlowChangeRate(flowGetter: () -> NetFlow): FlowChangeRate =
            FlowChangeRate(deadline = deadline, newRate = desiredDataRate, flowGetter = flowGetter)
    }

    data class FlowChangeRate(
        override val deadline: Ms,
        val newRate: Kbps,
        private val flowGetter: suspend () -> NetFlow
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            val flow = flowGetter()
            flow.setDemand(newRate)
            targetFlow = flow
        }
    }

    data class FlowStart(
        override val deadline: Ms,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: Kbps,
        val flowId: FlowId = NetFlow.nextId
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startFlow(
                transmitterId = from,
                destinationId = to,
                demand = desiredDataRate,
            ) ?. let { targetFlow = it }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)
    }

    data class FlowStop(
        override val deadline: Ms,
        private val flowIdGetter: suspend () -> FlowId
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.stopFlow(
                flowId = flowIdGetter.invoke()
            ) ?. let { targetFlow = it }
        }
    }
}
