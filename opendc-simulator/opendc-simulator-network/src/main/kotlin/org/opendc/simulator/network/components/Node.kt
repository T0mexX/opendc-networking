package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.Port
import org.opendc.simulator.network.components.internalstructs.RoutingTable
import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.forwarding.ForwardingPolicy
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger

/**
 * Type alias for improved understandability.
 */
internal typealias NodeId = Int

/**
 * Interface representing a node in a [Network].
 */
internal interface Node: FlowView {
    companion object { private val log by logger() }
    // TODO: allow connection between nodes without immediate vector routing forwarding,
    //  to optimize network building
    val id: NodeId
    val portSpeed: Kbps
    val ports: List<Port>
    val numOfPorts: Int get() { return ports.size }
    val forwardingPolicy: ForwardingPolicy

    val routingTable: RoutingTable
    val portToNode: MutableMap<NodeId, Port>

    /**
     * Property returning the number of [Node]s connected to ***this***.
     */
    private val numOfConnectedNodes: Int
        get() { return ports.count { it.isConnected() } }

    private fun getFreePort(): Port? =
        ports.firstOrNull { !it.isConnected() }

    /**
     * Connects ***this*** to `other` [Node] through a [Link],
     * updating the respective [RoutingTable]s.
     * @param[other]    the [Node] to connect to.
     */
    fun connect(other: Node) {
        if (other.id in portToNode.keys) {
            log.error("unable to connect $this to $other, nodes already connected")
            return
        }

        val freePort: Port = getFreePort()
            ?: let {
                log.error("unable to connect, maximum number of connected nodes reached ($numOfPorts).")
                return
            }

        val otherPort: Port = other.accept(this, freePort) ?: return

        portToNode[other.id] = freePort
        Link(freePort, otherPort)

        other.pushRoutingVector(routingTable.routingVector, vectorOwner = this)
    }

    /**
     * Accepts a connection request from `other` [Node],
     * second part of method [connect].
     * @see connect
     * @param[other]    node that is requesting to connect.
     */
    @Throws(IllegalStateException::class)
    private fun accept(other: Node, otherPort: Port): Port? {
        val freePort: Port = getFreePort()
            ?: let {
                log.error("Unable to accept connection, maximum number of connected nodes reached ($numOfPorts).")
                return null
            }

        Link(freePort, otherPort)

        portToNode[other.id] = freePort
        other.pushRoutingVector(routingTable.routingVector, vectorOwner = this)

        return freePort
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

        portToNode.forEach { (_, port) ->
            val adjNode: Node = port.linkIn?.opposite(port)?.node ?: return@forEach
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

    fun totDataRateOf(flowId: FlowId): Kbps =
        ports.sumOf { it.incomingFlows[flowId]?.dataRate ?: .0 }

    fun notifyFlowChange(flow: Flow) {
        forwardingPolicy.forwardFlow(forwarder = this, flow.id, flow.finalDestId)
    }

    fun routingTableToString(): String = routingTable.toString()

    override fun totIncomingDataRateOf(flowId: FlowId): Kbps =
        ports.sumOf { it.incomingFlows[flowId]?.dataRate ?: .0 }

    override fun totOutgoingDataRateOf(flowId: FlowId): Kbps =
        ports.sumOf { it.outgoingFlows[flowId]?.dataRate ?: .0 }

    override fun allTransitingFlowsIds(): Collection<FlowId> =
        portToNode.flatMap { (_, port) ->
            port.incomingFlows.keys + port.outgoingFlows.keys
        }.toSet()
}
