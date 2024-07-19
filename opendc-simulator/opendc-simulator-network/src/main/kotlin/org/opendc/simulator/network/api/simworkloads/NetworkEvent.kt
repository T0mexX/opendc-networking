package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import kotlin.system.measureNanoTime

internal abstract class NetworkEvent(val deadline: ms): Comparable<NetworkEvent> {
    private companion object { private val log by logger() }
    lateinit var targetFlow: NetFlow

    internal open fun involvedIds(): Set<NodeId> = setOf()
    protected abstract suspend fun exec(controller: NetworkController)

    suspend fun execIfNotPassed(controller: NetworkController) {
        val msSinceLastUpdate: ms = deadline - controller.instantSrc.millis()
        if (msSinceLastUpdate < 0)
            return log.error("unable to execute network event, deadline is passed")

        controller.advanceBy(msSinceLastUpdate)
        this.exec(controller)
    }

    override fun compareTo(other: NetworkEvent): Int =
        (this.deadline - other.deadline).toInt()

    /**
     * Only one flow is allowed between 2 nodes.
     */
    class FlowUpdate(
        deadline: ms,
        private val from: NodeId,
        private val to: NodeId,
        private val desiredDataRate: Kbps
    ): NetworkEvent(deadline) {
        override suspend fun exec(controller: NetworkController) {
            controller.startOrUpdateFlow(
                transmitterId = from,
                destinationId = to,
                desiredDataRate = desiredDataRate
            ) ?. let { targetFlow = it }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)
    }

    class FlowChangeRate(
        deadline: ms,
        private val newRate: Kbps,
        private val flowGetter: () -> NetFlow
    ): NetworkEvent(deadline) {
        override suspend fun exec(controller: NetworkController) {
            val flow = flowGetter()
            flow.setDesiredDataRate(newRate)
            targetFlow = flow
        }
    }

    class FlowStart(
        deadline: ms,
        private val from: NodeId,
        private val to: NodeId,
        private val desiredDataRate: Kbps,
        private val flowId: FlowId = IdDispenser.nextFlowId
    ): NetworkEvent(deadline) {
        override suspend fun exec(controller: NetworkController) {
            controller.startFlow(
                transmitterId = from,
                destinationId = to,
                desiredDataRate = desiredDataRate,
                flowId = flowId
            ) ?. let { targetFlow = it }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)
    }

    class FlowStop(
        deadline: ms,
        private val flowIdGetter: () -> FlowId
    ): NetworkEvent(deadline) {
        override suspend fun exec(controller: NetworkController) {
            controller.stopFlow(
                flowId = flowIdGetter.invoke()
            ) ?. let { targetFlow = it }
        }
    }
}
