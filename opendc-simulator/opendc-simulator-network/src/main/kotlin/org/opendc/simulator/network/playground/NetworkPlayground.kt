package org.opendc.simulator.network.playground//package org.opendc.simulator.network

import com.github.ajalt.clikt.core.CliktCommand
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.api.NetworkEnergyRecorder
import org.opendc.simulator.network.components.connect
import org.opendc.simulator.network.components.disconnect
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.utils.Result
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.whenMatch
import org.slf4j.Logger
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.exitProcess

// TODO: solve disconnection bug since coroutine refactor
//public fun main(args: Array<String>): Unit = NetworkPlayground().main(args)

private fun Logger.invNode(id: NodeId) {
    this.error("invalid node id $id")
}

private fun Logger.unableToParseId(str: String) {
    this.error("unable to parse id $str")
}

@OptIn(ExperimentalSerializationApi::class)
private class NetworkPlayground: CliktCommand() {

    companion object { val log by logger() }

    private val file: File = File("resources/TOBEDEL.json")
    private val network: Network
    private val energyRecorder: NetworkEnergyRecorder
    private val env: PlaygroundEnv
    private val supervisorJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { ctx, throwable ->
        log.error(
            "command ${ctx[CoroutineName]?.name ?: "unknown"} encountered an unexpected exception:" +
            throwable.stackTraceToString()
        )
    }
    private val playgroundScope =
        CoroutineScope(
            Dispatchers.Default +
                CoroutineName("network playground") +
                supervisorJob +
                exceptionHandler
        )

    init {
        if (file.exists()) {
            val jsonReader = Json { ignoreUnknownKeys = true }
            val networkSpecs: Specs<Network> = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            network = networkSpecs.build()
            log.info("starting network built from file ${file.name} ${network.nodesFmt()}")
        } else {
            println("file not provided or invalid, falling back to an empty custom network...")
            network = CustomNetwork()
        }

        energyRecorder = NetworkEnergyRecorder(network)
        env = PlaygroundEnv(network = network, energyRecorder = energyRecorder)
    }



    override fun run() = runBlocking {
        val networkRunner = network.launch()

        with(playgroundScope) {

            while (true) {
                val str: String = readlnOrNull() ?: continue

                whenMatch(str) {
                    Cmd.NEW_SWITCH.regex { with(Cmd.NEW_SWITCH) { exec(this@regex, env) } }
                    Cmd.RM_NODE.regex { with(Cmd.RM_NODE) { exec(this@regex, env) } }
                    Cmd.NEW_FLOW.regex { with(Cmd.NEW_FLOW) { exec(this@regex, env) } }
                    Cmd.RM_FLOW.regex { with(Cmd.RM_FLOW) { exec(this@regex, env) } }
                    Cmd.NEW_LINK.regex { with(Cmd.NEW_LINK) { exec(this@regex, env) } }
                    Cmd.RM_LINK.regex { with(Cmd.RM_LINK) { exec(this@regex, env) } }
                    Cmd.ENERGY_REPORT.regex { with(Cmd.ENERGY_REPORT) { exec(this@regex, env) } }
                    Cmd.FLOWS.regex { with(Cmd.FLOWS) { exec(this@regex, env) } }
                    Cmd.FLOWS_OF.regex { with(Cmd.FLOWS_OF) { exec(this@regex, env) } }
                    Cmd.QUIT.regex { with(Cmd.QUIT) { exec(this@regex, env) } }
                    otherwise { println("Unrecognized command.") }
                }
            }
        }
    }
}

private enum class Cmd {
    NEW_SWITCH {
        override val regex = Regex("\\s*(c|core|)(?:s|switch)\\s+(\\d+)\\s+([\\d.]+)\\s+(\\d+)\\s*")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                val customNet: CustomNetwork = (env.network as? CustomNetwork)
                    ?: return@cmdJob cancelAfter { log.error("adding a switch is not allowed unless the network is custom type") }

                val groups: List<String> = result.groupValues
                val id: NodeId = groups[2].toLongOrNull()?.let {
                    if (customNet.nodes.containsKey(it))
                        return@cmdJob cancelAfter { log.error("unable to add node, id $it already present") }

                    it
                } ?: return@cmdJob cancelAfter { log.error("unable to parse id") }

                val portSpeed: DataRate = json.decodeFromString(groups[3])
                    ?: return@cmdJob cancelAfter { log.error("unable to parse port speed") }
                val numOfPorts: Int = groups[4].toIntOrNull()
                    ?: return@cmdJob cancelAfter { log.error("unable to parse number of ports") }
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
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                val customNet: CustomNetwork = (env.network as? CustomNetwork)
                    ?: return@cmdJob cancelAfter { log.error("removing node is not allowed unless the network is of custom type") }

                val groups: List<String> = result.groupValues
                val nodeId: NodeId =
                    groups[1].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[1]) }

                when (val it = customNet.rmNode(nodeId)) {
                    is Result.ERROR -> log.error("unable to remove node. Reason: ${it.msg}")
                    is Result.SUCCESS -> log.info("node successfully removed")
                }
            }
    },
    NEW_FLOW {
        override val regex: Regex = Regex("\\s*(?:flow|f)\\s+(\\d+)\\s*(?:->| )\\s*(\\d+)\\s+(\\d+)\\s*")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                val groups: List<String> = result.groupValues
                val senderId: NodeId = groups[1].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[1]) }
                val destId: NodeId = groups[2].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[2]) }
                val dataRate: DataRate = json.decodeFromString(groups[3])
                    ?: return@cmdJob cancelAfter { log.error("unable to parse data-rate '${groups[3]}'") }

                val newFLow = NetFlow(
                    demand = dataRate,
                    transmitterId = senderId,
                    destinationId = destId,
                )

                env.network.startFlow(newFLow)?.let {
                    log.info("successfully create $newFLow")
                } ?: log.error("unable to create flow.")
            }
    },
    RM_FLOW {
        override val regex = Regex("\\s*rm\\s+(?:f|flow)\\s+(\\d+)\\s*")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                val groups: List<String> = result.groupValues
                val flowId: FlowId = groups[1].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[1]) }

                env.network.stopFlow(flowId)
                    ?.let { log.error("unable to stop flow.") }
                    ?: log.info("flow successfully stopped")
            }
    },
    NEW_LINK {
        override val regex = Regex("\\s*(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                val groups: List<String> = result.groupValues
                val node1Id: NodeId = groups[1].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[1]) }
                val node2Id: NodeId = groups[2].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[2]) }
                val node1: Node = env.network.nodes[node1Id] ?: return@cmdJob cancelAfter { log.invNode(node1Id) }
                val node2: Node = env.network.nodes[node2Id] ?: return@cmdJob cancelAfter { log.invNode(node2Id) }

                runCatching { node1.connect(node2) }.also {
                    if (it.isFailure) log.error("unable to create link. Reason: ${it.exceptionOrNull()?.message}")
                    else log.info("link successfully created")
                }
            }
    },
    RM_LINK {
        override val regex = Regex("\\s*rm\\s+(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                val groups: List<String> = result.groupValues
                val node1Id: NodeId = groups[1].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[1]) }
                val node2Id: NodeId = groups[2].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(groups[2]) }
                val node1: Node = env.network.nodes[node1Id] ?: return@cmdJob cancelAfter { log.invNode(node1Id) }
                val node2: Node = env.network.nodes[node2Id] ?: return@cmdJob cancelAfter { log.invNode(node2Id) }

                runCatching { node1.disconnect(node2) }.also {
                    if (it.isFailure) log.error("unable to remove link. Reason: ${it.exceptionOrNull()?.message}")
                    else log.info("nodes successfully disconnected")
                }
            }
    },
    ENERGY_REPORT {
        override val regex = Regex("energy-report|report|er|r")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            // TODO: better change to use network controller and its time and sync with commands like advance by to be added
            launchNamed cmdJob@ {
                println(env.energyRecorder.getFmtReport())
            }
},
    FLOWS {
        override val regex = Regex("(flows|f)")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                env.network.awaitStability()

                println("==== Flows ====")
                println(
                    "id".padEnd(5) +
                        "sender".padEnd(10) +
                        "dest".padEnd(10) +
                        "demand (Kbps)".padEnd(20) +
                        "throughput (Kbps)".padEnd(20)
                )
                env.network.flowsById.values.forEach { flow ->
                    println(
                        flow.id.toString().padEnd(5) +
                            flow.transmitterId.toString().padEnd(10) +
                            flow.destinationId.toString().padEnd(10) +
                            String.format("%.3f", flow.demand.toKbps()).padEnd(20) +
                            String.format("%.3f", flow.throughput.toKbps()).padEnd(20)
                    )
                }
            }
    },
    FLOWS_OF {
        override val regex = Regex("(?:flows|f) (\\d+)")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job =
            launchNamed cmdJob@ {
                env.network.awaitStability()

                val nodeId: NodeId =
                    result.groupValues[1].toLongOrNull() ?: return@cmdJob cancelAfter { log.unableToParseId(result.groupValues[1]) }
                val node: Node = env.network.nodes[nodeId] ?: return@cmdJob cancelAfter { log.invNode(nodeId) }
                println("\n==== Flows in node $nodeId ====")
                println(node.getFmtFlows())
            }
    },
    QUIT {
        override val regex = Regex("q|quit")
        override fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job {
            exitProcess(status = 0)
        }
    };

    val log by logger(this.name)
    abstract val regex: Regex
    abstract fun CoroutineScope.exec(result: MatchResult, env: PlaygroundEnv): Job

    /**
     * Wrapper for [CoroutineScope.launch] that launches the coroutine with the command [this.name].
     */
    fun CoroutineScope.launchNamed(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job = this.launch(context + CoroutineName(this@Cmd.name), block = block)

    companion object {
        val json = Json
    }
}


private data class PlaygroundEnv (
    val network: Network,
    val energyRecorder: NetworkEnergyRecorder,
//    val excHandler: CoroutineExceptionHandler
)


private fun CoroutineScope.cancelAfter(f: () -> Unit) {
    f.invoke()
    this.cancel()
}


