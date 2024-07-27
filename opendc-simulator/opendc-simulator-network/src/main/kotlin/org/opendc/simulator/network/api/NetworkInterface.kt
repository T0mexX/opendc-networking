package org.opendc.simulator.network.api

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Internet
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger


/**
 * Type alias for improved understandability.
 */
public typealias NodeId = Long

/**
 * Interface that can be used by other modules
 * to control the networking of a single node.
 */
public class NetworkInterface internal constructor(
    private val node: EndPointNode,
    private val netController: NetworkController,
    public var owner: String = "unknown"
): AutoCloseable {

    /**
     * Physical id associated with this node.
     */
    public val nodeId: NodeId = node.id


    private val subInterfaces = mutableListOf<NetworkInterface>()
    private val flows = mutableMapOf<FlowId, NetFlow>()


    @JvmOverloads
    public fun getSubInterface(
        owner: String = "unknown",
//        bwLimitMbps: Mbps? = null, // TODO: implement
//        reserved: Mbps? = null // TODO: implement (difficult if host node also acts as forwarder)
    ): NetworkInterface {
        val newIface = NetworkInterface(
            node = this.node,
            netController = this.netController,
            owner = owner
        )
        subInterfaces.add(newIface)

        return newIface
    }

    /**
     * Starts a network flow ([NetFlow]) from this node to the node with id [destinationId].
     *
     * If [destinationId] is null than the flow is directed to the [Internet].
     *
     * @param[destinationId]                the id of the destination node.
     * @param[demand]              the initial data rate of the new flow.
     * @param[throughputChangeHandler]      a function that is invoked whenever the
     * throughput of the flow changes. The function second parameter is the old value,
     * while the third one is the new value.
     *
     * @return  a handle on the flow. One is able
     * to adjust the desired data rate modifying [NetFlow.demand].
     */
    @JvmSynthetic
    public suspend fun startFlowSus(
        destinationId: NodeId = INTERNET_ID,
        demand: Kbps = .0,
        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow? {
        val newFlow: NetFlow? = netController.startFlow(
            transmitterId = this.nodeId,
            destinationId = destinationId,
            desiredDataRate = demand,
            dataRateOnChangeHandler = throughputChangeHandler,
        )

        newFlow?.let { flows[newFlow.id] = newFlow }

        return newFlow
    }

    /**
     * Non suspending overload for java interoperability.
     */
    @JvmOverloads
    public fun startFlow(
        destinationId: NodeId = INTERNET_ID,
        demand: Kbps = .0,
        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow? = runBlocking { startFlowSus(destinationId = destinationId, demand = demand, throughputChangeHandler = throughputChangeHandler) }

    /**
     * Stops the flow with id [id] if it exists, and it belongs to this node.
     */
    @JvmSynthetic
    public suspend fun stopFlowSus(id: FlowId) {
        flows.remove(id)?.let {
            netController.stopFlow(id)
        } ?: log.error(
            "network interface with owner '$owner' tried to stop flow which does not own"
        )
    }

    /**
     * Non suspending overload fot java interoperability.
     */
    public fun stopFlow(id: FlowId): Unit = runBlocking { stopFlowSus(id = id) }

    /**
     * @return a handle on the flow with id [id] if it exists, and it belongs to this node.
     */
    public fun getMyFlow(id: FlowId): NetFlow? {
        TODO()
    }

    /**
     * Starts a flow from the [Internet] to this node.
     * Alternatively one can use [NetworkController.internetNetworkInterface].
     *
     * See [startFlowSus] for parameters docs.
     */
    @JvmSynthetic
    public suspend fun fromInternetSus(
        demand: Kbps = .0,
        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow {
        val newFlow: NetFlow = netController.internetNetworkInterface.startFlowSus(
            destinationId = this.nodeId,
            demand = demand,
            throughputChangeHandler = throughputChangeHandler,
        )!!

        flows[newFlow.id] = newFlow

        return newFlow
    }

    /**
     * Non suspending overload fot java interoperability.
     */
    @JvmOverloads
    public fun fromInternet(
        demand: Kbps = .0,
        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow = runBlocking { fromInternetSus(demand = demand, throughputChangeHandler = throughputChangeHandler) }


    override fun close() {
        subInterfaces.forEach { it.close() }
        runBlocking {
            flows.keys.forEach {
                launch { netController.stopFlow(it) }
            }
        }
    }

    public companion object {
        internal val log by logger()
    }
}
