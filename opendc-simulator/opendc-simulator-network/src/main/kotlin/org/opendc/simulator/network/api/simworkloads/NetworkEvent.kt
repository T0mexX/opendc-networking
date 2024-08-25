/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.api.simworkloads

import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.logger

public abstract class NetworkEvent : Comparable<NetworkEvent> {
    private companion object {
        private val log by logger()
    }

    internal abstract val deadline: Time
    internal val targetFlow: NetFlow get() = targetFlowGetter()
    protected open var targetFlowGetter: () -> NetFlow = { throw RuntimeException("target flow for network event $this is not defined yet") }

    internal open fun involvedIds(): Set<NodeId> = setOf()

    protected abstract suspend fun NetworkController.exec()

    internal suspend fun NetworkController.execIfNotPassed() {
        val msSinceLastUpdate: Time = deadline - Time.ofInstantFromEpoch(instantSrc.instant())
        if (msSinceLastUpdate < Time.ZERO) {
            return log.error(
                "unable to execute network event, " +
                    "deadline is passed (deadline=${deadline.toInstantFromEpoch()}, " +
                    "currentInstant=${instantSrc.instant()})",
            )
        }

        this.advanceBy(msSinceLastUpdate)

        exec()
    }

    override fun compareTo(other: NetworkEvent): Int = this.deadline.compareTo(other.deadline)

    /**
     * Only one flow is allowed between 2 nodesById.
     */
    internal data class FlowUpdate(
        override val deadline: Time,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: DataRate,
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startOrUpdateFlow(
                transmitterId = from,
                destinationId = to,
                demand = desiredDataRate,
            ) ?. let { targetFlowGetter = { it } }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)

        fun toFlowStart(): FlowStart = FlowStart(deadline = deadline, from = from, to = to, desiredDataRate = desiredDataRate)

        fun toFlowChangeRate(flowGetter: () -> NetFlow): FlowChangeRate =
            FlowChangeRate(deadline = deadline, newRate = desiredDataRate, targetFlowGetter = flowGetter)
    }

    internal data class FlowChangeRate(
        override val deadline: Time,
        val newRate: DataRate,
        override var targetFlowGetter: () -> NetFlow,
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            val flow = targetFlow
            flow.setDemand(newRate)
        }
    }

    internal data class FlowStart(
        override val deadline: Time,
        val from: NodeId,
        val to: NodeId,
        val desiredDataRate: DataRate,
        val flowId: FlowId = runBlocking { NetFlow.nextId() },
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.startFlow(
                transmitterId = from,
                destinationId = to,
                demand = desiredDataRate,
            ) ?. let { targetFlowGetter = { it } }
        }

        override fun involvedIds(): Set<NodeId> = setOf(from, to)
    }

    internal data class FlowStop(
        override val deadline: Time,
        override var targetFlowGetter: () -> NetFlow,
    ) : NetworkEvent() {
        override suspend fun NetworkController.exec() {
            this.stopFlow(
                flowId = targetFlow.id
            )
        }
    }
}
