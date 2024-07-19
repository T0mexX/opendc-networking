package org.opendc.simulator.network.components

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.NonSerializable
import kotlin.math.pow
import org.opendc.simulator.network.components.Switch.SwitchSpecs
import java.io.File
import kotlin.math.min

/**
 * Fat-tree network topology built based on the number of ports of the [SwitchSpecs] passed as parameter.
 * The number of ports is usually referred as ***k*** and should be a multiple of 2 and larger than 2.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
internal class FatTreeNetwork(
    coreSpecs: SwitchSpecs,
    aggrSpecs: SwitchSpecs,
    torSpecs: SwitchSpecs
): Network() {

    override val nodes: Map<NodeId, Node>
    override val endPointNodes: Map<NodeId, EndPointNode>
    override val netFlowById: MutableMap<Int, NetFlow> = HashMap()


    /**
     * Parameter that determines the topology which is defined as
     * equal to the minimum number of ports of all switches rounded down to even number.
     * Ideally all switches should have the same number of ports.
     * This value has to be even and larger than 2.
     */
    private val k: Int = minOf(coreSpecs.numOfPorts, aggrSpecs.numOfPorts, torSpecs.numOfPorts) / 2 * 2


    /**
     * The [FatTreePod]s that belong to ***this*** [FatTreeNetwork].
     */
    val pods: List<FatTreePod>

    /**
     * The [CoreSwitch]es that belong to ***this*** [FatTreeNetwork].
     */
    val coreSwitches: List<Switch>

    /**
     * The [HostNode]s that belong to ***this*** [FatTreeNetwork].
     */
    val leafs: List<HostNode>

    /**
     * The aggregation [Switch]es that belong to ***this*** [FatTreeNetwork].
     */
    val aggregationSwitches: List<Switch>

    /**
     * The Top of Rack [Switch]es that belong to ***this*** [FatTreeNetwork].
     */
    val torSwitches: List<Switch>

    override val internet: Internet

    init {
        require(k.isEven() && k > 2) { "Fat tree can only be built with even-port-number (>2) switches" }

        // to connect to abstract node "internet"
        @Suppress("NAME_SHADOWING")
        val coreSpecs = coreSpecs.copy(numOfPorts = coreSpecs.numOfPorts + 1)

        println("k = $k")
        pods = buildList { repeat(k) { add(FatTreePod(aggrSpecs, torSpecs)) } }

        val coreSwitchesChunked = buildList {
            repeat(k * k / 4) { add(coreSpecs.buildCoreSwitchFromSpecs()) }
        }.chunked(k / 2)

        pods.forEach { pod ->
            pod.aggrSwitches.forEachIndexed { switchIdx, switch ->
                coreSwitchesChunked[switchIdx].forEach { runBlocking { it.connect(switch) } }
            }
        }

        coreSwitches = coreSwitchesChunked.flatten()

        aggregationSwitches = pods.flatMap { it.aggrSwitches }

        torSwitches = pods.flatMap { it.torSwitches }

        leafs = pods.flatMap { it.hostNodes }

        internet = Internet().connectedTo(coreSwitches)

        nodes = buildMap {
            putAll((leafs + torSwitches + aggregationSwitches + coreSwitches).associateBy { it.id })
            check(internet.id !in this)
            { "unable to create network: one node has id $INTERNET_ID, which is reserved for internet abstraction" }
            put(internet.id, internet)
        }

        endPointNodes = (coreSwitches + leafs + internet).associateBy { it.id }
    }


    /**
     * A pod that belongs tot the enclosing [FatTreeNetwork] instance.
     * @param[aggrSpecs]    specifications of the switches in the *aggregation layer*.
     * @param[torSpecs]     specifications of the switches in the *access layer* (also called Top of Rack switches).
     */
    inner class FatTreePod(aggrSpecs: SwitchSpecs, torSpecs: SwitchSpecs) {
        /**
         * [HostNode]s that belong to ***this*** pod.
         */
        val hostNodes: List<HostNode>

        /**
         * ToR [Switch]es that belong to ***this*** pod.
         */
        val torSwitches: List<Switch>

        /**
         * Aggregation [Switch]es that belong to ***this*** pod.
         */
        val aggrSwitches: List<Switch>


        init {
            val k: Int = min(aggrSpecs.numOfPorts, torSpecs.numOfPorts)
            hostNodes = buildList {
                repeat( (k / 2).toDouble().pow(2.0).toInt() ) { add(HostNode(IdDispenser.nextNodeId, 1000.0, 1)) } // TODO: change HostNode building
            }

            torSwitches = buildList {
                    repeat (k / 2) { add(torSpecs.buildFromSpecs()) }
                }

            hostNodes.forEachIndexed { index, server ->
                runBlocking { server.connect( torSwitches[index / (k / 2)] ) }
            }

            aggrSwitches = torSwitches
                .map { _ ->
                    val newSwitch = aggrSpecs.buildFromSpecs()
                    torSwitches.forEach { runBlocking { newSwitch.connect(it) } }
                    newSwitch
                }.toList()
        }
    }

    companion object {
        internal fun fromFile(file: File) =
            Specs.fromFile<FatTreeNetwork>(file).buildFromSpecs()

        operator fun invoke(allSwitchSpecs: SwitchSpecs): FatTreeNetwork =
            FatTreeNetwork(coreSpecs = allSwitchSpecs, aggrSpecs = allSwitchSpecs, torSpecs = allSwitchSpecs)
    }




    @Serializable
    @SerialName("fat-tree-specs")
    internal data class FatTreeTopologySpecs(
        val name: String = "Default",
        val switchSpecs: SwitchSpecs? = null,
        val coreSwitchSpecs: SwitchSpecs? = null,
        val aggrSwitchSpecs: SwitchSpecs? = null,
        val torSwitchSpecs: SwitchSpecs? = null
    ): Specs<FatTreeNetwork> {
        /**
         * Returns a [FatTreeNetwork] if the specs are valid, throws error otherwise.
         */
        override fun buildFromSpecs(): FatTreeNetwork {
            val error by lazy { IllegalArgumentException("Unable to build Fat-Tree from specs. " +
                "Either define all layers specs or provide a general switch specs") }
            return FatTreeNetwork(
                coreSpecs = coreSwitchSpecs ?: run { switchSpecs ?: throw error },
                aggrSpecs = aggrSwitchSpecs ?: run { switchSpecs ?: throw error },
                torSpecs = torSwitchSpecs ?: run { switchSpecs ?: throw error },
            )
        }
    }
}

private fun Int.isEven(): Boolean = this % 2 == 0


