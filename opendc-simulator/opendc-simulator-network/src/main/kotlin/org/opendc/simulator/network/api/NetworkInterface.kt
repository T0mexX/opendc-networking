package org.opendc.simulator.network.api

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshotOf
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Internet
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.tracker.NodeFlowTracker
import org.opendc.simulator.network.flow.tracker.TrackerMode
import org.opendc.simulator.network.units.DataRate
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
    internal val netController: NetworkController,
    public var owner: String = "unknown"
): AutoCloseable {

    /**
     * Physical id associated with this node.
     */
    public val nodeId: NodeId = node.id


    private val subInterfaces = mutableListOf<NetworkInterface>()
    internal val flowsById = mutableMapOf<FlowId, NetFlow>()
    private val flowsByName = mutableMapOf<String, NetFlow>()
    private val genFromInternet = mutableMapOf<FlowId, NetFlow>()

    public fun nodeSnapshot(): NodeSnapshot = netController.snapshotOf(node.id)!!


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
        demand: DataRate = DataRate.ZERO,
        name: String? = null,
        throughputChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)? = null
    ): NetFlow? {
        val newFlow: NetFlow? = netController.startFlow(
            transmitterId = this.nodeId,
            destinationId = destinationId,
            demand = demand,
            name = name ?: NetFlow.DEFAULT_NAME,
            throughputOnChangeHandler = throughputChangeHandler,
        )?.also {
            // If name is not default.
            if (it.name != NetFlow.DEFAULT_NAME) flowsByName[name]
            flowsById[it.id] = it
        }

        return newFlow
    }

    /**
     * Non suspending overload for java interoperability.
     * JvmOverloads annotation not allowed with *value-class* arguments.
     */
    public fun startFlow(
        destinationId: NodeId = INTERNET_ID,
        demand: DataRate = DataRate.ZERO,
        throughputChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)? = null
    ): NetFlow? = runBlocking { startFlowSus(destinationId = destinationId, demand = demand, throughputChangeHandler = throughputChangeHandler) }
    @JvmOverloads public fun startFlow(destinationId: NodeId = INTERNET_ID, throughputChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)? = null): NetFlow? = runBlocking { startFlowSus(destinationId = destinationId, throughputChangeHandler = throughputChangeHandler) }

    /**
     * Stops the flow with id [id] if it exists, and it belongs to this node.
     */
    @JvmSynthetic
    public suspend fun stopFlowSus(id: FlowId) {
        flowsById.remove(id)?.let {
            netController.stopFlow(id)
        } ?: genFromInternet.remove(id)?.let {
            netController.internetNetworkInterface.stopFlowSus(id)
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
    public fun getMyFlow(id: FlowId): NetFlow? =
        flowsById[id]

    public fun getMyFlow(name: String): NetFlow? =
        flowsByName[name]

    /**
     * Starts a flow from the [Internet] to this node.
     * Alternatively one can use [NetworkController.internetNetworkInterface].
     *
     * See [startFlowSus] for parameters docs.
     */
    @JvmSynthetic
    public suspend fun fromInternetSus(
        demand: DataRate = DataRate.ZERO,
        throughputChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)? = null
    ): NetFlow {
        val newFlow: NetFlow = netController.internetNetworkInterface.startFlowSus(
            destinationId = this.nodeId,
            demand = demand,
            throughputChangeHandler = throughputChangeHandler,
        )!!

        genFromInternet[newFlow.id] = newFlow

        return newFlow
    }

    /**
     * Non suspending overload fot java interoperability.
     * JvmOverloads annotation not allowed with *value-class* arguments.
     */
    public fun fromInternet(
        demand: DataRate = DataRate.ZERO,
        throughputChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)? = null
    ): NetFlow = runBlocking { fromInternetSus(demand = demand, throughputChangeHandler = throughputChangeHandler) }
    public fun fromInternet(): NetFlow = runBlocking { fromInternetSus() }
    public fun fromInternet(demand: DataRate): NetFlow = runBlocking { fromInternetSus(demand = demand) }
    public fun fromInternet(throughputChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)?): NetFlow = runBlocking { fromInternetSus(throughputChangeHandler = throughputChangeHandler) }



    override fun close() {
        subInterfaces.forEach { it.close() }
        runBlocking {
            flowsById.keys.forEach {
                launch { netController.stopFlow(it) }
            }
        }
    }

    public companion object {
        internal val log by logger()

        private fun NetFlow.belongsTo(netIface: NetworkInterface): Boolean =
            id in netIface.flowsById || netIface.subInterfaces.any { belongsTo(it) }
    }
}


