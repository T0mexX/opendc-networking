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
    val routingVector: Map<NodeId, Int>
        get() { return table.map { it.key to it.value.first().numOfHops }.toMap() }

    /**
     * This bool is updated each time a vector is merged in the routing table.
     * It is true if the merge resulted in some changes, false otherwise
     */
    var isChanged: Boolean = false
        private set

    /**
     * Routing table mapping [Node] id to its [PossiblePath], containing cost and nextHop.
     */
    private val table: HashMap<NodeId, MutableSet<PossiblePath>> = HashMap()

    /**
     * Add a [PossiblePath] to the routing table if it is minimal and not already included.
     * @param[pathToAdd] The possible path to add to the table.
     * TODO: change to include non minimal paths, needed for energy/traffic aware software defined networking
     */
    private fun addPath(pathToAdd: PossiblePath) {
        val destId: NodeId = pathToAdd.destinationId
        if (destId == ownerId) return
        val possiblePaths = table[destId] ?: let {
            table[destId] = mutableSetOf(pathToAdd)
            isChanged = true
            return
        }
        val currNumOfHops: Int = possiblePaths.first().numOfHops
        if (possiblePaths.contains(pathToAdd)) return
        when {
            pathToAdd.numOfHops < currNumOfHops -> {
                possiblePaths.clear()
                possiblePaths.add(pathToAdd)
            }
            pathToAdd.numOfHops == currNumOfHops -> {
                possiblePaths.add(pathToAdd)
            }
            else -> return
        }
        isChanged = true
    }

    /**
     * Returns possible minimal cost paths to a certain destination id.
     * @param[destId]   destination id whose possible minimal paths are to be returned.
     */
    fun getPossiblePathsTo(destId: NodeId): Set<PossiblePath>? = table[destId]

    /**
     * Merges routing vector of adjacent [Node].
     * @param[routingVector]    routing vector of adjacent [Node], mapping destination id to its cost (number of hops).
     */
    fun mergeRoutingVector(routingVector: Map<NodeId, Int>, owner: Node) {
        isChanged = false
        addPath(PossiblePath(owner.id, numOfHops = 1, nextHop = owner))
        routingVector.forEach { (destId, numOfHops) ->
            if (destId == this.ownerId) return@forEach
            addPath(PossiblePath(destId, numOfHops + 1, owner))
        }
    }

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
