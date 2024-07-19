package org.opendc.simulator.network.components

import org.opendc.simulator.network.components.internalstructs.RoutingVect
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.components.internalstructs.port.connect
import org.opendc.simulator.network.components.internalstructs.port.disconnect
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.logger

private val log by Unit.logger("NodeConnectionExt")

internal suspend fun Node.connect(other: Node, duplex: Boolean = true, linkBW: Kbps? = null) {
    updtChl.whileReceivingLocked {
        if (other.id in portToNode.keys)
            return@whileReceivingLocked log.warn("unable to connect $this to $other, nodes already connected")

        val freePort: Port = getFreePort()
            ?: throw RuntimeException("unable to connect, max num of connected nodes reached ($numOfPorts).")

        val otherPort: Port = other.accept(this)
            ?: throw RuntimeException("unable to connect, node $other refused connection")

        portToNode[other.id] = freePort

        // TODO: right now everything above the port/link layer does not support non-duplex connections
        //  in the future it would be nice to add
        freePort.connect(otherPort, duplex = duplex, linkBW = linkBW)

        val otherVect: RoutingVect = other.exchangeRoutVect(routingTable.getVect(), vectOwner = this)
        routingTable.mergeRoutingVector(otherVect, vectOwner = other)
        shareRoutingVect(except = listOf(other))
        with(flowHandler) { updtAllRouts() }

        updateAllFlows()
    }
}

/**
 * Accepts a connection request from `other` [Node],
 * second part of method [connect].
 * @see connect
 * @param[other]    node that is requesting to connect.
 */
private suspend fun Node.accept(other: Node): Port? =
    updtChl.whileReceivingLocked {
        val freePort: Port = getFreePort()
            ?: let {
                log.error("Unable to accept connection, maximum number of connected nodes reached ($numOfPorts).")
                return@whileReceivingLocked null
            }

        portToNode[other.id] = freePort

        freePort
    }

internal suspend fun Node.disconnect(other: Node) =
    this.disconnect(other = other, notifyOther = true)

/**
 * Disconnects all ports of ***this*** node.
 */
internal suspend fun Node.disconnectAll() {
    portToNode.mapNotNull { (_, port) ->
        port.otherEndNode
    }.forEach { disconnect(it, notifyOther = true) }
}

/**
 * Disconnects ***this*** from [other].
 * @return      [Result.SUCCESS] on success, [Result.ERROR] otherwise.
 */
private suspend fun Node.disconnect(other: Node, notifyOther: Boolean) {
    updtChl.whileReceivingLocked {
        val portToOther: Port = portToNode[other.id]
            ?: return@whileReceivingLocked log.warn("unable to disconnect $this from $other, nodes are not connected")


        if (notifyOther)
            other.disconnect(this, notifyOther = false)

        portToOther.disconnect()

        routingTable.removeNextHop(other)
        portToNode.remove(other.id)

        shareRoutingVect(exchange = true)
    }
}



/**
 * Returns an available port if exists, else null.
 */
private fun Node.getFreePort(): Port? =
    ports.firstOrNull { !it.isConnected }

/**
 * Merges [routVect] in the [this.routingTable], updating flows
 * and sharing its updated routing vector if needed.
 * @return      its own routing vector.
 */
private suspend fun Node.exchangeRoutVect(routVect: RoutingVect, vectOwner: Node): RoutingVect {
    routingTable.mergeRoutingVector(routVect, vectOwner)

    if (!routingTable.isTableChanged) return routingTable.getVect()
    with (flowHandler) { updtAllRouts() }
    updateAllFlows()
    notifyAdjNodes()

    if (!routingTable.isVectChanged) return routingTable.getVect()
    shareRoutingVect(except = listOf(vectOwner))

    return routingTable.getVect()
}

/**
 * Shares ***this*** routing vector with all adjacent nodes, except those in [except].
 * @param[except]       the list of nodes to which not share the vector with.
 * @param[exchange]     determines if ***this*** should receive and merge other's vectors as well.
 * If so, the process continues until one iteration of the function is completed without that ***this*** routing vector changes.
 */
private tailrec suspend fun Node.shareRoutingVect(except: Collection<Node> = listOf(), exchange: Boolean = false) {
    portToNode.forEach { (_, port) ->
        val adjNode: Node = port.otherEndNode ?: return@forEach
        if (adjNode !in except) {
            if (exchange) {
                val otherVect: RoutingVect = adjNode.exchangeRoutVect(routingTable.getVect(), vectOwner = this)
                routingTable.mergeRoutingVector(otherVect, vectOwner = adjNode)
                if (!routingTable.isTableChanged) return@forEach


                if (!routingTable.isVectChanged) return@forEach
                shareRoutingVect(except = listOf(adjNode), exchange = true)
                return
            } else
                adjNode.exchangeRoutVect(routingTable.getVect(), vectOwner = this)
        }
    }

    with (flowHandler) { updtAllRouts() }
    updateAllFlows()
    notifyAdjNodes()
}
