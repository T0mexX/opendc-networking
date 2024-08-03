package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.Time
import org.opendc.simulator.network.utils.logger

internal abstract class NetworkEvent: Comparable<NetworkEvent> {
    private companion object {
        private val log by logger()
    }

    abstract val deadline: Time
    lateinit var targetFlow: NetFlow



    internal open fun involvedIds(): Set<NodeId> = setOf()
    protected abstract suspend fun NetworkController.exec()

    suspend fun NetworkController.execIfNotPassed() {
        val msSinceLastUpdate: Time = deadline - Time.ofInstantFromEpoch(instantSrc.instant())
        if (msSinceLastUpdate < Time.ZERO)
            return log.error("unable to execute network event, " +
                "deadline is passed (deadline=${deadline.toInstantFromEpoch()}, " +
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
        override val deadline: Time,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: DataRate
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
        override val deadline: Time,
        val newRate: DataRate,
        private val flowGetter: suspend () -> NetFlow
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            val flow = flowGetter()
            flow.setDemand(newRate)
            targetFlow = flow
        }
    }

    data class FlowStart(
        override val deadline: Time,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: DataRate,
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
        override val deadline: Time,
        private val flowIdGetter: suspend () -> FlowId
    ): NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.stopFlow(
                flowId = flowIdGetter.invoke()
            ) ?. let { targetFlow = it }
        }
    }
}
