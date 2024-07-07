package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.errAndGet
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms

/**
 * Interface representing a network of [Node]s.
 */
internal interface Network {
    private companion object { private val log by logger() }

    /**
     * Maps [NodeId]s to their corresponding [Node]s, which are part of the [Network]
     */
    val nodes: Map<NodeId, Node>

    /**
     * Maps [NodeId]s to their corresponding [EndPointNode]s, which are part of the [Network].
     * This map is a subset of [nodes].
     */
    val endPointNodes: Map<NodeId, EndPointNode>

    /**
     * Maps flow ids to their corresponding [NetFlow].
     */
    val netFlowById: MutableMap<FlowId, NetFlow>

    val internet: Internet

    /**
     * Starts a [NetFlow] if the flow can be established.
     * @param[flow] the flow to be established.
     */
    fun startFlow(flow: NetFlow): Result {
        if (flow.desiredDataRate <= 0)
            return log.errAndGet("Unable to start flow, data rate should be positive.")

        val sender: EndPointNode = endPointNodes[flow.transmitterId]
            ?: return log.errAndGet("Unable to start flow $flow, sender does not exist or it is not able to start a flow")

        val receiver: EndPointNode = endPointNodes[flow.destinationId]
            ?: return log.errAndGet("Unable to start flow $flow, receiver does not exist or it is not able to start a flow")

        netFlowById[flow.id] = flow
        receiver.addReceivingEtoEFlow(flow)
        sender.startFlow(flow)

        return SUCCESS
    }

    /**
     * Stops a [NetFlow] if the flow is running through the network.
     * @param[flowId]   id of the flow to be stopped.
     */
    fun stopFlow(flowId: FlowId): Result {
        netFlowById[flowId]?. let { eToEFlow ->
            endPointNodes[eToEFlow.transmitterId]
                ?.stopFlow(eToEFlow)
                ?.also {
                    endPointNodes[eToEFlow.destinationId]
                        ?.rmReceivingEtoEFlow(eToEFlow.id)
                }?.also {
                    netFlowById.remove(flowId)
                }
        } ?: return log.errAndGet("unable to stop flow with id $flowId")

        return SUCCESS
    }

    /**
     * Returns a string with all [nodes] string representations, each in one line.
     */
    fun allNodesToString(): String {
        val sb = StringBuilder()
        nodes.forEach { sb.append("\n$it") }
        sb.append("\n")

        return sb.toString()
    }

    fun advanceBy(ms: ms) {
        netFlowById.values.forEach { it.advanceBy(ms) }
    }
}

internal inline fun <reified T> Network.getNodesById(): Map<NodeId, T> {
    return nodes.values.filterIsInstance<T>().associateBy { (it as Node).id }
}
