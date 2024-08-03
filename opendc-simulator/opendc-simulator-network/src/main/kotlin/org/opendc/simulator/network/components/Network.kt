@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package org.opendc.simulator.network.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.Time
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.errAndNull
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.withWarn


/**
 * Interface representing a network of [Node]s.
 */
@Serializable(with = NonSerializable::class)
public sealed class Network {


    internal val validator: StabilityValidator = StabilityValidator()

    private val networkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Maps [NodeId]s to their corresponding [Node]s, which are part of the [Network]
     */
    internal abstract val nodes: Map<NodeId, Node>

    /**
     * Maps [NodeId]s to their corresponding [EndPointNode]s, which are part of the [Network].
     * This map is a subset of [nodes].
     */
    internal abstract val endPointNodes: Map<NodeId, EndPointNode>

    /**
     * Maps flow ids to their corresponding [NetFlow].
     */
    internal val flowsById = mutableMapOf<FlowId, NetFlow>()

    internal val flowsByName = mutableMapOf<String, NetFlow>()

    internal abstract val internet: Internet

    internal var runnerJob: Job? = null
        private set

    internal val isRunning: Boolean
        get() = runnerJob?.isActive ?: false



    /**
     * Starts a [NetFlow] if the flow can be established.
     * @param[flow] the flow to be established.
     * @return      `null` if the flow could not be started, otherwise the flow itself.
     */
    internal suspend fun startFlow(flow: NetFlow): NetFlow? {
        // If name defined and already exists.
        if (flow.name != NetFlow.DEFAULT_NAME && flow.name in flowsByName)
            return null

        if (flow.demand < DataRate.ZERO)
            return log.errAndNull("Unable to start flow, data rate should be >= 0.")

        val sender: EndPointNode = endPointNodes[flow.transmitterId]
            ?: return log.errAndNull("Unable to start flow $flow, sender does not exist or it is not able to start a flow")

        val receiver: EndPointNode = endPointNodes[flow.destinationId]
            ?: return log.errAndNull("Unable to start flow $flow, receiver does not exist or it is not able to start a flow")

        flowsById[flow.id] = flow
        if (flow.name != NetFlow.DEFAULT_NAME)
            flowsByName[flow.name] = flow

        receiver.addReceivingEtoEFlow(flow)

        sender.startFlow(flow)

        return flow
    }

    /**
     * Stops a [NetFlow] if the flow is running through the network.
     * @param[flowId]   id of the flow to be stopped.
     */
    internal suspend fun stopFlow(flowId: FlowId): NetFlow? =
        flowsById[flowId]?.let { eToEFlow ->
            endPointNodes[eToEFlow.transmitterId]
                ?.stopFlow(eToEFlow.id)
                ?.let {
                    endPointNodes[eToEFlow.destinationId]
                        ?.rmReceivingEtoEFlow(eToEFlow.id)
                    flowsById.remove(flowId)
                    eToEFlow
                }
        } ?: log.withWarn(null, "unable to stop flow with id $flowId")


    internal fun resetFlows() = runBlocking {
        flowsById.keys.toSet().forEach { stopFlow(it) }
    }

    /**
     * Returns a string with all [nodes] string representations, each in one line.
     */
    internal fun allNodesToString(): String {
        val sb = StringBuilder()
        nodes.forEach { sb.append("\n$it") }
        sb.append("\n")

        return sb.toString()
    }

    internal fun advanceBy(time: Time) {
        flowsById.values.forEach { it.advanceBy(time) }
    }

    internal suspend fun awaitStability() {
        validator.awaitStability()
    }

    internal fun launch(): Job {
        runBlocking { runnerJob?.cancelAndJoin() }
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

    public fun nodesFmt(): String =
        "\n" + """
            | == NETWORK INFO ===
            | num of core switches: ${getNodesById<CoreSwitch>().size}
            | num of host nodes: ${getNodesById<HostNode>().size}
            | num of nodes: ${nodes.size} (including INTERNET abstract node)
        """.trimIndent()


    internal companion object {
        private val log by logger()

        internal inline fun <reified T: Node> Network.getNodesById(): Map<NodeId, T> {
            return this.nodes.values.filterIsInstance<T>().associateBy { it.id }
        }
    }
}

/**
 * [NodeId] reserved for internet representation (for inter-datacenter communication).
 */
public const val INTERNET_ID: NodeId = NodeId.MIN_VALUE


