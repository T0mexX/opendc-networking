package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.logger

/**
 * Interface representing a network of [Node]s.
 */
internal interface Network {
    companion object { val log by logger() }

    /**
     * Maps [NodeId]s to their corresponding [Node]s, which are part of the [Network]
     */
    val nodes: MutableMap<NodeId, Node>

    /**
     * Maps [NodeId]s to their corresponding [EndPointNode]s, which are part of the [Network].
     * This map is a subset of [nodes].
     */
    val endPointNodes: Map<NodeId, EndPointNode>

    /**
     * Maps flow ids to their corresponding [EndToEndFlow].
     */
    val endToEndFlows: MutableMap<FlowId, EndToEndFlow>

    /**
     * Starts a [EndToEndFlow] if the flow can be established.
     * @param[flow] the flow to be established.
     */
    fun startFlow(flow: EndToEndFlow) {
        if (flow.desiredDataRate <= 0) { log.warn("Unable to start flow, data rate should be positive.") }
        if (flow.totalDataToTransmit <= 0) { log.warn("Unable to start flow. data size should be positive.") }

        val sender: EndPointNode = endPointNodes[flow.senderId]
            ?: run { log.error("Unable to start flow $flow, sender does not exist or it is not able to start a flow"); return }
        val receiver: EndPointNode = endPointNodes[flow.destId]
            ?: run { log.error("Unable to start flow $flow, receiver does not exist or it is not able to start a flow"); return }

        endToEndFlows[flow.flowId] = flow
        receiver.addReceivingEtoEFlow(flow)
        sender.startFlow(flow)
    }

    /**
     * Stops a [EndToEndFlow] if the flow is running through the network.
     * @param[flowId]   id of the flow to be stopped.
     */
    fun stopFlow(flowId: FlowId): Result {
        endToEndFlows[flowId]?. let { eToEFlow ->
            endPointNodes[eToEFlow.senderId]
                ?.stopFlow(eToEFlow)
                ?.also {
                    endPointNodes[eToEFlow.destId]
                        ?.rmReceivingEtoEFlow(eToEFlow.flowId)
                }?.also {
                    endToEndFlows.remove(flowId)
                }
        } ?: let {
            log.error("unable to stop flow with id $flowId")
            return FAILURE
        }

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
}
