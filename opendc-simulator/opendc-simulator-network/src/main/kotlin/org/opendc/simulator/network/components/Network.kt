@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package org.opendc.simulator.network.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.errAndGet
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms


/**
 * Interface representing a network of [Node]s.
 */
@Serializable(with = NonSerializable::class)
internal abstract class Network {


    protected val validator = StabilityValidator()

    protected val networkScope = CoroutineScope(Dispatchers.Default)

    /**
     * Maps [NodeId]s to their corresponding [Node]s, which are part of the [Network]
     */
    internal abstract val nodes: Map<NodeId, Node>

    /**
     * Maps [NodeId]s to their corresponding [EndPointNode]s, which are part of the [Network].
     * This map is a subset of [nodes].
     */
    abstract val endPointNodes: Map<NodeId, EndPointNode>

    /**
     * Maps flow ids to their corresponding [NetFlow].
     */
    abstract val netFlowById: MutableMap<FlowId, NetFlow>

    protected abstract val internet: Internet

    protected var runnerJob: Job? = null

    protected val isRunning: Boolean
        get() = runnerJob?.isActive ?: false



    /**
     * Starts a [NetFlow] if the flow can be established.
     * @param[flow] the flow to be established.
     */
    suspend fun startFlow(flow: NetFlow): Result {
        if (flow.desiredDataRate < 0)
            return log.errAndGet("Unable to start flow, data rate should be >= 0.")

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
    suspend fun stopFlow(flowId: FlowId): Result {
        netFlowById[flowId]?.let { eToEFlow ->
            endPointNodes[eToEFlow.transmitterId]
                ?.stopFlow(eToEFlow.id)
                ?.also {
                    endPointNodes[eToEFlow.destinationId]
                        ?.rmReceivingEtoEFlow(eToEFlow.id)
                }?.also {
                    netFlowById.remove(flowId)
                }
        } ?: return log.errAndGet("unable to stop flow with id $flowId")

        return SUCCESS
    }

    fun resetFlows() = runBlocking {
        netFlowById.keys.toSet().forEach { stopFlow(it) }
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

    suspend fun awaitStability() {
        validator.awaitStability()
    }

    internal fun launch(): Job {
        validator.reset()
        runnerJob = networkScope.launch {
            nodes.forEach { (_, n) ->
                launch {
                    n.run(validator.Invalidator())
                }
            }
        }

        return runnerJob !!
    }


    companion object {
        private val log by logger()

        inline fun <reified T: Node> Network.getNodesById(): Map<NodeId, T> {
            return this.nodes.values.filterIsInstance<T>().associateBy { it.id }
        }
    }
}

/**
 * [NodeId] reserved for internet representation (for inter-datacenter communication).
 */
public const val INTERNET_ID: NodeId = NodeId.MIN_VALUE


