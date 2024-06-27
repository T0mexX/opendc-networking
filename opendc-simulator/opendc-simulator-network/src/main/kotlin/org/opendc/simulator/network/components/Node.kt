package org.opendc.simulator.network.components

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.components.internalstructs.FlowsTable
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.utils.Kbps

/**
 * Type alias for improved understandability.
 */
internal typealias NodeId = Int

/**
 * Interface representing a node in a [Network].
 */
internal interface Node {
    // TODO: allow connection between nodes without immediate vector routing forwarding,
    //  to optimize network building
    val id: NodeId
    val portSpeed: Kbps
    val numOfPorts: Int
    val forwardingPolicy: ForwardingPolicy

    /**
     * Handler called when an incoming [Flow] changes its data rate.
     */
    val dataRateOnChangeHandler: OnChangeHandler<Flow, Kbps>

    /**
     * Maps the [NodeId] to the [Link] which is connected to the [Node] with that id.
     */
    val linksToAdjNodes: MutableMap<NodeId, Link>

    val routingTable: RoutingTable
    val flowTable: FlowsTable

    /**
     * Property returning the number of [Node]s connected to ***this***.
     */
    private val numOfConnectedNodes: Int
        get() { return linksToAdjNodes.size }

    /**
     * Connects ***this*** to `other` [Node] through a [Link],
     * updating the respective [RoutingTable]s.
     * @param[other]    the [Node] to connect to.
     */
    @Throws(IllegalStateException::class)
    fun connect(other: Node) {
        check(numOfConnectedNodes < numOfPorts)
            { "Unable to connect switch, maximum number of connected nodes reached ($numOfPorts)." }

        linksToAdjNodes[other.id] = Link(sender = this, receiver = other)
        other.accept(this)
        other.pushRoutingVector(routingTable.routingVector, vectorOwner = this)
    }

    /**
     * Accepts a connection request from `other` [Node],
     * second part of method [connect].
     * @see connect
     * @param[other]    node that is requesting to connect.
     */
    @Throws(IllegalStateException::class)
    private fun accept(other: Node) {
        check(numOfConnectedNodes < numOfPorts)
            { "Unable to connect node (id=${this.id}, maximum number of connected nodes reached ($numOfPorts)." }

        linksToAdjNodes[other.id] = Link(sender = this, receiver = other)
        other.pushRoutingVector(routingTable.routingVector, vectorOwner = this)
    }

    /**
     * Called from an adjacent connected [Node] (`vectorOwner`) when its [RoutingTable] changes.
     * Pushes `other`'s routing vector into ***this***, updating ***this*** routing table accordingly,
     * and chain pushing to adjacent nodes if necessary.
     * @param[routingVector]    routing vector pushed by `vectorOwner`.
     * @param[vectorOwner]      adjacent node that pushes the routing vector.
     */
    private fun pushRoutingVector(routingVector: Map<NodeId, Int>, vectorOwner: Node) {
        routingTable.mergeRoutingVector(routingVector, vectorOwner)
        if (!routingTable.isChanged) return

        linksToAdjNodes.values.forEach { link: Link ->
            val adjNode: Node = link.opposite(this)
            if (adjNode !== vectorOwner) adjNode.pushRoutingVector(routingTable.routingVector, vectorOwner = this)
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
    fun getAvrgPortUtilization(): Double {
        // TODO: implement
        return 0.5
    }

    /**
     * Pushes a new flow from an adjacent [Node] into ***this***.
     * If needed, it forwards the flow based on the [forwardingPolicy].
     */
    fun pushNewFlow(flow: Flow)


    fun routingTableToString(): String = routingTable.toString()
}
