package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Result.*
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.logger

/**
 * Type alias for improved understandability.
 */
internal typealias NodeId = Int

/**
 * Type alias representing the routing vector of a [Node]. For improved readability.
 */
internal typealias RoutingVect = Map<NodeId, Int>

/**
 * Interface representing a node in a [Network].
 */
internal interface Node: FlowView {
    companion object { private val log by logger() }
    // TODO: allow connection between nodes without immediate vector routing forwarding,
    //  to optimize network building

    /**
     * ID of the node. Uniquely identifies the node in the [Network].
     */
    val id: NodeId

    /**
     * Port speed in Kbps full duplex.
     */
    val portSpeed: Kbps

    /**
     * Ports of ***this*** [Node], full duplex.
     */
    val ports: List<Port>

    /**
     * Number of ports of ***this*** [Node].
     */
    val numOfPorts: Int get() { return ports.size }

    /**
     * Policy that determines how [Flow]s are forwarded.
     */
    val forwardingPolicy: ForwardingPolicy

    /**
     * Contains network information about the routs
     * available to reach each node in the [Network].
     */
    val routingTable: RoutingTable

    /**
     * Maps each connected [Node]'s id to the [Port] is connected to.
     */
    val portToNode: MutableMap<NodeId, Port>

    /**
     * Property returning the number of [Node]s connected to ***this***.
     */
    private val numOfConnectedNodes: Int
        get() { return ports.count { it.isConnected() } }

    /**
     * Returns an available port if exists, else null.
     */
    private fun getFreePort(): Port? =
        ports.firstOrNull { !it.isConnected() }

    /**
     * Connects ***this*** to [other] [Node] through a [Link],
     * updating the respective [RoutingTable]s (if ports are available on both ends).
     * @param[other]    the [Node] to connect to.
     * @return          [Result.SUCCESS] on success, [Result.FAILURE] otherwise.
     */
    fun connect(other: Node): Result {
        if (other.id in portToNode.keys) {
            log.error("unable to connect $this to $other, nodes already connected")
            return FAILURE
        }

        val freePort: Port = getFreePort()
            ?: let {
                log.error("unable to connect, maxi num of connected nodes reached ($numOfPorts).");
                return FAILURE
            }

        val otherPort: Port = other.accept(this, freePort) ?: return FAILURE

        portToNode[other.id] = freePort
        Link(freePort, otherPort)

        val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.vector, vectOwner = this)
        routingTable.mergeRoutingVector(otherVect, vectOwner = other)
        shareRoutingVect(except = listOf(other))

        updateAllFlows()
        return SUCCESS
    }

    /**
     * Accepts a connection request from `other` [Node],
     * second part of method [connect].
     * @see connect
     * @param[other]    node that is requesting to connect.
     */
    private fun accept(other: Node, otherPort: Port): Port? {
        val freePort: Port = getFreePort()
            ?: let {
                log.error("Unable to accept connection, maximum number of connected nodes reached ($numOfPorts).")
                return null
            }

        Link(freePort, otherPort)

        portToNode[other.id] = freePort

        return freePort
    }

    /**
     * Updates forwarding of all flows transiting through ***this*** node.
     */
    fun updateAllFlows() {
        allTransitingFlowsIds().forEach {
            forwardingPolicy.forwardFlow(forwarder = this, flowId = it)
        }
    }

    /**
     * Disconnects ***this*** from [other].
     * @return      [Result.SUCCESS] on success, [Result.FAILURE] otherwise.
     */
    fun disconnect(other: Node, notifyOther: Boolean = true): Result {
        val portToOther: Port = portToNode[other.id]
            ?: let {
                log.error("unable to disconnect $this from $other, nodes are not connected")
                return FAILURE
            }


        if (notifyOther &&
            other.disconnect(this, notifyOther = false) == FAILURE)
            return FAILURE

        if (portToOther.disconnect() == FAILURE)
            return FAILURE

        routingTable.removeNextHop(other)
        portToNode.remove(other.id)

        shareRoutingVect(exchange = true)

        updateAllFlows()

        return SUCCESS
    }

    /**
     * Disconnects all ports of ***this*** node.
     */
    fun disconnectAll() {
        portToNode.toMap().forEach { (_, port) ->
             port.remoteConnectedPort?. let { disconnect(it.node) }
        }
    }

    /**
     * Merges [routVect] in the [routingTable], updating flows
     * and sharing its updated routing vector if needed.
     * @return      its own routing vector.
     */
    private fun exchangeRoutVect(routVect: RoutingVect, vectOwner: Node): RoutingVect {
        routingTable.mergeRoutingVector(routVect, vectOwner)
        if (!routingTable.isChanged) return routingTable.vector

        updateAllFlows()
        shareRoutingVect(except = listOf(vectOwner))

        return routingTable.vector
    }

    /**
     * Shares ***this*** routing vector with all adjacent nodes, except those in [except].
     * @param[except]       the list of nodes to which not share the vector with.
     * @param[exchange]     determines if ***this*** should receive and merge other's vectors as well.
     * If so, the process continues until one iteration of the function is completed without that ***this*** routing vector changes.
     */
    private tailrec fun shareRoutingVect(except: Collection<Node> = listOf(), exchange: Boolean = false) {
        portToNode.forEach { (_, port) ->
            val adjNode: Node = port.remoteConnectedPort?.node ?: return@forEach
            if (adjNode !in except) {
                if (exchange) {
                    val otherVect: RoutingVect = adjNode.exchangeRoutVect(routingTable.vector, vectOwner = this)
                    routingTable.mergeRoutingVector(otherVect, vectOwner = adjNode)
                    if (!routingTable.isChanged) return@forEach
                    updateAllFlows()
                    shareRoutingVect(except = listOf(adjNode), exchange = true)
                    return
                } else
                    adjNode.exchangeRoutVect(routingTable.vector, vectOwner = this)
            }
        }
    }

    /**
     * Returns the number of active ports.
     */
    fun getActivePortNum(): Int = this.numOfPorts // TODO: change when energy policy that turn off ports are implemented

    /**
     * Returns the average utilization among all ports.
     * It is useful to compute energy consumption even
     * though dynamic consumption has very little impact overall.
     */
    fun getAvrgPortUtilization(): Double =
        portToNode.values.sumOf { it.utilization } / portToNode.size

    /**
     * Returns the total data rate (received from other
     * nodes or generated by ***this***) corresponding to [flowId].
     * It also represents the data rate that needs to be forwarded.
     * @param[flowId]       id of the flow whose total data rate is to be returned.
     */
    fun totDataRateOf(flowId: FlowId): Kbps =
        ports.sumOf { it.incomingFlows[flowId]?.dataRate ?: .0 }

    /**
     * Called by a [Port] whenever an incoming flow
     * has changed its data rate or a new flow is received.
     * It updates forwarding based on [forwardingPolicy].
     * @param[flowId]   the incoming flow that has changed.
     */
    fun notifyFlowChange(flowId: FlowId) {
        forwardingPolicy.forwardFlow(forwarder = this, flowId)
    }

    /**
     * Return a [String] representation of the [routingTable].
     */
    fun routingTableToString(): String = routingTable.toString()

    override fun totIncomingDataRateOf(flowId: FlowId): Kbps =
        ports.sumOf { it.incomingFlows[flowId]?.dataRate ?: .0 }

    override fun totOutgoingDataRateOf(flowId: FlowId): Kbps =
        ports.sumOf { it.outgoingFlows[flowId]?.dataRate ?: .0 }

    override fun throughputOutOf(flowId: FlowId): Kbps =
        ports.sumOf { it.throughputOutOf(flowId) }

    override fun allTransitingFlowsIds(): Collection<FlowId> =
        portToNode.flatMap { (_, port) ->
            port.incomingFlows.keys + port.outgoingFlows.keys
        }.toSet()
}
