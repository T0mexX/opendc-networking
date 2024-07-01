package org.opendc.simulator.network.components

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.logger

/**
 * Network that is not built following a specific algorithm.
 * It can be built from json with specific format.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
internal class CustomNetwork(
    nodes: List<Node> = mutableListOf()
): Network {
    companion object { val log by logger() }

    override val endToEndFlows: MutableMap<FlowId, EndToEndFlow> = HashMap()

    override val nodes: MutableMap<NodeId, Node> =
        nodes.associateBy { it.id }.toMutableMap()

    override val networkEndPointNodes: MutableMap<NodeId, EndPointNode> =
        nodes.filterIsInstance<EndPointNode>()
            .associateBy { it.id }.toMutableMap()


    /**
     * Connects [Node]s of ***this*** based on link-list passed as parameter.
     * If the link cannot be established, a warning message is logged and the link is ignored.
     * @param[links]    the list of pair representing the links to be established in the network.
     */
    fun connectFromLinkList(links: List<Pair<NodeId, NodeId>>) {
        fun warnOfUnsetLink(id1: NodeId, id2: NodeId) {
            log.warn("Link from (NodeId=$id1) <-> (NodeId=$id2) could not be established, " +
                "one of the nodes does not exist or it's connecting to itself.")
        }

        links.forEach { (id1, id2) ->
            val node1: Node = nodes[id1] ?: let {warnOfUnsetLink(id1, id2); return@forEach}
            val node2: Node = nodes[id2] ?: let {warnOfUnsetLink(id1, id2); return@forEach}
            if (node1 === node2) { warnOfUnsetLink(id1, id2); return@forEach }
            node1.connect(node2)
        }
    }

    /**
     * Adds a node to the network if a node with same id is not already present.
     * @param[node] node to add.
     */
    fun addNode(node: Node) {
        nodes[node.id]?.let { log.error("unable to add node $node, id already present"); return }
        nodes[node.id] = node
        (node as? EndPointNode)?.let { networkEndPointNodes[node.id] = node }
    }



    /**
     * [Specs] of [CustomNetwork], deserializable from json.
     * From ***this*** the corresponding custom network can be built.
     */
    @Serializable
    @SerialName("custom-network-topology-specs")
    internal data class CustomNetworkSpecs(
        val nodesSpecs: List<Specs<Node>>,
        @Serializable(with = LinkListSerializer::class)
        val links: List<Pair<NodeId, NodeId>>
    ): Specs<CustomNetwork> {

        companion object { val log by logger()}

        override fun buildFromSpecs(): CustomNetwork {
            val nodes: List<Node> = nodesSpecs.map { it.buildFromSpecs() }
            val distinctNodes = nodes.distinctBy { it.id }
            if (nodes.size != distinctNodes.size)
                log.warn("Some nodes with already existing ids got filtered out.")
            val customNetwork = CustomNetwork(distinctNodes)
            customNetwork.connectFromLinkList(links)
            return customNetwork
        }
    }



    /**
     * Deserializer of a json array of array (**size 2**) of ints `[[1, 2], [2, 3]]`,
     * into a link list (`List<Pair<NodeID, NodeId>>`).
     * - Filters out arrays (links) with size not equal to 2.
     * - Filters out links that try to connect a node to itself.
     */
    private class LinkListSerializer: KSerializer<List<Pair<NodeId, NodeId>>> {
        companion object { private val log by logger() }

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("link-list", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): List<Pair<NodeId, NodeId>> {
            val listOfArrays: List<List<NodeId>> =
                decoder.decodeSerializableValue(kotlinx.serialization.serializer())
            val linkList: List<Pair<NodeId, NodeId>> = listOfArrays
                .filterNot { if (it.size != 2) { log.warn("Invalid link represented by array $it, size of array should be 2"); return@filterNot true } else false }
                .map { it[0] to it[1] }
                .filterNot { if (it.first == it.second) { log.warn("Invalid link $it, a node may not connect to itself"); return@filterNot true } else false }

            return linkList
        }

        override fun serialize(encoder: Encoder, value: List<Pair<NodeId, NodeId>>) {
            throw IllegalStateException("A link list (List<Pair<NodeId, NodeId>>) should never be serialized.")
        }
    }
}



