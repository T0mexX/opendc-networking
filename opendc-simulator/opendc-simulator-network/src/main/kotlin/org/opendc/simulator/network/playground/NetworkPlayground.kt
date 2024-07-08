package org.opendc.simulator.network.playground//package org.opendc.simulator.network

import com.github.ajalt.clikt.core.CliktCommand
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.api.NetworkEnergyRecorder
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.logger
import org.slf4j.Logger
import java.io.File
import kotlin.system.exitProcess


public fun main(args: Array<String>): Unit = NetworkPlayground().main(args)

private fun Logger.invNode(id: NodeId) {
    this.error("invalid node id $id")
}

private fun Logger.unableToParseId(str: String) {
    this.error("unable to parse id $str")
}

@OptIn(ExperimentalSerializationApi::class)
private class NetworkPlayground: CliktCommand() {

    private val file: File = File("resources/examples/custom-network-topology-specs-example4.json")
    private val network: Network
    private val energyRecorder: NetworkEnergyRecorder
    private val env: PlaygroundEnv

    init {
        if (file.exists()) {
            val jsonReader = Json { ignoreUnknownKeys = true }
            val networkSpecs: Specs<Network> = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            network = networkSpecs.buildFromSpecs()
        } else network = CustomNetwork()

        StaticECMP.eToEFlows = network.netFlowById

        energyRecorder = NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())
        env = PlaygroundEnv(network = network, energyRecorder = energyRecorder)
        println(network.allNodesToString())
    }

    override fun run() {
        println("Network Built!")
        while (true) {
            val str: String = readlnOrNull() ?: return
            whenMatch(str) {
                Cmd.NEW_SWITCH.regex { Cmd.NEW_SWITCH.exec(this, env) }
                Cmd.RM_NODE.regex {Cmd.RM_NODE.exec(this, env) }
                Cmd.NEW_FLOW.regex { Cmd.NEW_FLOW.exec(this, env) }
                Cmd.RM_FLOW.regex { Cmd.RM_FLOW.exec(this, env) }
                Cmd.NEW_LINK.regex { Cmd.NEW_LINK.exec(this, env) }
                Cmd.RM_LINK.regex { Cmd.RM_LINK.exec(this, env) }
                Cmd.ENERGY_REPORT.regex { Cmd.ENERGY_REPORT.exec(this, env) }
                Cmd.FLOWS.regex { Cmd.FLOWS.exec(this, env) }
                Cmd.FLOWS_OF.regex { Cmd.FLOWS_OF.exec(this, env) }
                Cmd.QUIT.regex { Cmd.QUIT.exec(this, env) }
                otherwise { println("Unrecognized command.") }
            }
        }
    }
}

private enum class Cmd {
    NEW_SWITCH {
        override val regex = Regex("\\s*(c|core|)(?:s|switch)\\s+(\\d+)\\s+([\\d.]+)\\s+(\\d+)\\s*")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val customNet: CustomNetwork = (env.network as? CustomNetwork)
                ?: return log.error("adding a switch is not allowed unless the network is custom type")

            val groups: List<String> = result.groupValues
            val id: NodeId = groups[2].toLongOrNull()?.let {
                if (customNet.nodes.containsKey(it))
                    return log.error("unable to add node, id $it already present")

                it
            } ?: return log.error("unable to parse id")

            val portSpeed: Kbps = groups[3].toDoubleOrNull() ?: return log.error("unable to parse port speed")
            val numOfPorts: Int = groups[4].toIntOrNull() ?: return log.error("unable to parse number of ports")
            val newSwitch: Switch = let {
                if (groups[1].isEmpty())
                    Switch(
                        id = id,
                        portSpeed = portSpeed,
                        numOfPorts = numOfPorts
                    )
                else
                    CoreSwitch(
                        id = id,
                        portSpeed = portSpeed,
                        numOfPorts = numOfPorts
                    )
            }

            when (val it = customNet.addNode(newSwitch)) {
                is Result.ERROR -> log.error("unable to add switch. Reason: ${it.msg}")
                is Result.SUCCESS -> log.info("successfully added $newSwitch to network")
            }
        }
    },
    RM_NODE {
        override val regex = Regex("\\s*rm\\s+(?:n|node)\\s+(\\d+)\\s*")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val customNet: CustomNetwork = (env.network as? CustomNetwork)
                ?: return log.error("removing node is not allowed unless the network is of custom type")

            val groups: List<String> = result.groupValues
            val nodeId: NodeId = groups[1].toLongOrNull() ?: return log.unableToParseId(groups[1])

            when(val it = customNet.rmNode(nodeId)) {
                is Result.ERROR -> log.error("unable to remove node. Reason: ${it.msg}")
                is Result.SUCCESS -> log.info("node successfully removed")
            }
        }
    },
    NEW_FLOW {
        override val regex: Regex = Regex("\\s*(?:flow|f)\\s+(\\d+)\\s*(?:->| )\\s*(\\d+)\\s+(\\d+)\\s*")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val groups: List<String> = result.groupValues
            val senderId: NodeId = groups[1].toLongOrNull() ?: return log.unableToParseId(groups[1])
            val destId: NodeId = groups[2].toLongOrNull() ?: return log.unableToParseId(groups[2])
            val dataRate: Double = groups[3].toDouble()

            val newFLow = NetFlow(
                id = FlowIdDispatcher.nextId,
                desiredDataRate = dataRate,
                transmitterId = senderId,
                destinationId = destId,
            )

            when (val it = env.network.startFlow(newFLow)) {
                is Result.ERROR -> log.error("unable to create flow. Reason: ${it.msg}")
                is Result.SUCCESS -> log.info("successfully create $newFLow")
            }
        }
    },
    RM_FLOW {
        override val regex = Regex("\\s*rm\\s+(?:f|flow)\\s+(\\d+)\\s*")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val groups: List<String> = result.groupValues
            val flowId: FlowId = groups[1].toIntOrNull() ?: return log.unableToParseId(groups[1])

            when(val it = env.network.stopFlow(flowId)) {
                is Result.ERROR -> log.error("unable to stop flow. Reason: ${it.msg}")
                is Result.SUCCESS -> log.info("flow successfully stopped")
            }
        }
    },
    NEW_LINK {
        override val regex = Regex("\\s*(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val groups: List<String> = result.groupValues
            val node1Id: NodeId = groups[1].toLongOrNull() ?: return log.unableToParseId(groups[1])
            val node2Id: NodeId = groups[2].toLongOrNull() ?: return log.unableToParseId(groups[2])
            val node1: Node = env.network.nodes[node1Id] ?: return log.invNode(node1Id)
            val node2: Node = env.network.nodes[node2Id] ?: return log.invNode(node2Id)

            when (val it = node1.connect(node2)) {
                is Result.ERROR -> log.error("unable to create link. Reason: ${it.msg}")
                is Result.SUCCESS -> log.info("link successfully created")
            }
        }
    },
    RM_LINK {
        override val regex = Regex("\\s*rm\\s+(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val groups: List<String> = result.groupValues
            val node1Id: NodeId = groups[1].toLongOrNull() ?: return log.unableToParseId(groups[1])
            val node2Id: NodeId = groups[2].toLongOrNull() ?: return log.unableToParseId(groups[2])
            val node1: Node = env.network.nodes[node1Id] ?: return log.invNode(node1Id)
            val node2: Node = env.network.nodes[node2Id] ?: return log.invNode(node2Id)

            when (val it = node1.disconnect(node2)) {
                is Result.ERROR -> log.error("unable to remove link. Reason: ${it.msg}")
                is Result.SUCCESS -> log.info("nodes successfully disconnected")
            }
        }
    },
    ENERGY_REPORT {
        override val regex = Regex("energy-report|report|er|r")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            println(env.energyRecorder.getFmtReport())
        }
    },
    FLOWS {
        override val regex = Regex("(flows|f)")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            println("==== Flows ====")
            println(
                "id".padEnd(5) +
                "sender".padEnd(10) +
                "dest".padEnd(10) +
                "desired data rate".padEnd(20) +
                "actual data rate".padEnd(20)
            )
            env.network.netFlowById.values.forEach { flow ->
                println(
                    flow.id.toString().padEnd(5) +
                    flow.transmitterId.toString().padEnd(10) +
                    flow.destinationId.toString().padEnd(10) +
                    flow.desiredDataRate.toString().padEnd(20) +
                    flow.throughput.toString().padEnd(20)
                )
            }
        }
    },
    FLOWS_OF {
        override val regex = Regex("(?:flows|f) (\\d+)")
        override fun exec(result: MatchResult, env: PlaygroundEnv) {
            val nodeId: NodeId = result.groupValues[1].toLongOrNull() ?: return log.unableToParseId(result.groupValues[1])
            val node: Node = env.network.nodes[nodeId] ?: return log.invNode(nodeId)
            println("\n==== Flows in node $nodeId ====")
            println(node.getFmtFlows())
        }

    },
    QUIT {
        override val regex = Regex("q|quit")
        override fun exec(result: MatchResult, env: PlaygroundEnv) { exitProcess(status = 0) }
    };

    val log by logger(this.name)
    abstract val regex: Regex
    abstract fun exec(result: MatchResult, env: PlaygroundEnv)
}

private object FlowIdDispatcher {
    var nextId: Int = 0
        get() { val id = field; field++; return id }
}

private data class PlaygroundEnv (
    val network: Network,
    val energyRecorder: NetworkEnergyRecorder
)


private class PatternMatching(private val value: String) {
    private var handled = false

    operator fun Regex.invoke(block: MatchResult.() -> Unit) {
        if (handled) return
        matchEntire(value)?.block()?.also { handled = true }
    }

    fun otherwise(block: () -> Unit) {
        if (handled) return
        block()
    }
}

private fun whenMatch(value: String, block: PatternMatching.() -> Unit) = PatternMatching(value).block()
