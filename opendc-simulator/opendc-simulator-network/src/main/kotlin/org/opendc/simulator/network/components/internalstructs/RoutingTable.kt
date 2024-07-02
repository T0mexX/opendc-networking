package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Link

/**
 * Represents the routing table for the [Node] associated with the [ownerId] param.
 * @param[ownerId]  id of [Node] to which this table belongs.
 */
internal class RoutingTable(private val ownerId: NodeId) {

    /**
     * Maps the destination id to the cost (numOfHops) to that destination.
     * It is used to share routing information to other [Node]s
     */
    val vector: Map<NodeId, Int>
        get() {
            return table.map {
                it.key to (minNumOfHopsTo(it.key) ?: 0)
            }.filterNot { it.second == 0 }.toMap()
        }

    /**
     * This bool is updated each time a vector is merged in the routing table.
     * It is true if the merge resulted in some changes, false otherwise
     */
    var isChanged: Boolean = false
        private set

    /**
     * Routing table mapping [Node] id to its [PossiblePath], containing cost and nextHop.
     */
    private val table: HashMap<NodeId, MutableMap<NodeId, PossiblePath>> = HashMap()

    /**
    * Adds [pathToAdd] to the routing table. If a path with
     * the same next hop is already present, it is substituted.
    * @param[pathToAdd] The possible path to add to the table.
    */
    private fun addOrReplacePath(pathToAdd: PossiblePath) {
        val destId: NodeId = pathToAdd.destinationId
        if (destId == ownerId) return
        val possiblePaths = table[destId] ?: let {
            table[destId] = mutableMapOf(pathToAdd.nextHop.id to pathToAdd)
            isChanged = true
            return
        }

        if (possiblePaths[pathToAdd.nextHop.id] == pathToAdd) return

        possiblePaths[pathToAdd.nextHop.id] = pathToAdd
        isChanged = true
    }

    /**
     * Removes a [PossiblePath] form the routing table.
     * The combination of [destId] and [nextHopId] uniquely identifies a possible path in the table.
     * @param[destId]       final destination of the path to be removed.
     * @param[nextHopId]    the next hop [NodeId] of the path to be removed.
     */
    private fun rmPath(destId: NodeId, nextHopId: NodeId) {
        table[destId]?.remove(nextHopId)
        if (table[destId]?.isEmpty() == true)
            table.remove(destId)
        isChanged = true
    }

    /**
     * Returns possible minimal cost paths to a certain destination id.
     * @param[destId]   destination id whose possible minimal paths are to be returned.
     */
    fun getPossiblePathsTo(destId: NodeId): Collection<PossiblePath> =
        table.getOrDefault(destId, mapOf()).values

    /**
     * Merges routing vector of adjacent [Node].
     * @param[routingVector]    routing vector of adjacent [Node], mapping destination id to its cost (number of hops).
     */
    fun mergeRoutingVector(routingVector: Map<NodeId, Int>, vectOwner: Node) {
        isChanged = false
        addOrReplacePath(PossiblePath(vectOwner.id, numOfHops = 1, nextHop = vectOwner))

        val destToRemove: Set<NodeId> = table.keys.toSet() - routingVector.keys.toSet() - vectOwner.id
        destToRemove.forEach { rmPath(destId = it, nextHopId = vectOwner.id) }

        routingVector
            .forEach { (destId, numOfHops) ->
                addOrReplacePath(
                    PossiblePath(
                        destinationId = destId,
                        numOfHops = numOfHops + 1,
                        nextHop = vectOwner
                    )
                )
            }
    }

    /**
     * Removes all possible paths whose next hop is [node].
     */
    fun removeNextHop(node: Node) {
        table.forEach { (_, possPaths) ->
            possPaths.remove(node.id)
        }
        table.values.removeAll { it.isEmpty() }
    }

    /**
     * Returns the minimum distance between ***this*** and the node with id [destId].
     */
    private fun minNumOfHopsTo(destId: NodeId): Int? =
        table[destId]?.minOf { (_, possPath) -> possPath.numOfHops }

    override fun toString(): String = table.toString()

    /**
     * Represents a possible path to the [Node] corresponding to [destinationId].
     * @param[destinationId]    id of the final destination.
     * @param[numOfHops]        number of [Link]s that need to be traversed.
     * @param[nextHop]          adjacent [Node] to which forward flows if this path is to be used.
     */
    data class PossiblePath(
        val destinationId: NodeId,
        val numOfHops: Int,
        val nextHop: Node,
    ) : Comparable<PossiblePath> {
        override fun compareTo(other: PossiblePath): Int = this.numOfHops compareTo other.numOfHops
    }
}
