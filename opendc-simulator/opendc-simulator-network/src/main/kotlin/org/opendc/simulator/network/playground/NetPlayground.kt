/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.playground // package org.opendc.simulator.network

import com.github.ajalt.clikt.core.CliktCommand
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.api.NetEnRecorder
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.playground.cmds.AdvanceTime
import org.opendc.simulator.network.playground.cmds.Export
import org.opendc.simulator.network.playground.cmds.NewFlow
import org.opendc.simulator.network.playground.cmds.NewLink
import org.opendc.simulator.network.playground.cmds.NewSwitch
import org.opendc.simulator.network.playground.cmds.NodeInfo
import org.opendc.simulator.network.playground.cmds.PGCmd
import org.opendc.simulator.network.playground.cmds.EnReport
import org.opendc.simulator.network.playground.cmds.Quit
import org.opendc.simulator.network.playground.cmds.RmFlow
import org.opendc.simulator.network.playground.cmds.RmLink
import org.opendc.simulator.network.playground.cmds.RmNode
import org.opendc.simulator.network.playground.cmds.ShowFlows
import org.opendc.simulator.network.playground.cmds.ShowSnapshot
import org.opendc.simulator.network.utils.PatternMatching.Companion.whenMatch
import org.opendc.simulator.network.utils.logger
import java.io.File
import java.time.Instant

public fun main(args: Array<String>): Unit = NetworkPlayground().main(args)

@OptIn(ExperimentalSerializationApi::class)
private class NetworkPlayground : CliktCommand() {
    // TODO: let choose file from command line
    private val file: File = File("resources/exported8.json")

    /**
     * The network environment of this playground.
     */
    private val env: PGEnv

    /**
     * The supervisor job of the [playgroundScope].
     * Needed so that exceptions can be handled without the scope being canceled.
     */
    private val supervisorJob = SupervisorJob()

    /**
     * If exception that is not cancellation exception is throw, then it is caught and logged with ERROR level.
     */
    private val exceptionHandler =
        CoroutineExceptionHandler { ctx, throwable ->
            LOG.error(
                "command ${ctx[CoroutineName]?.name ?: "unknown"} encountered an unexpected exception:" +
                    throwable.stackTraceToString(),
            )
        }

    /**
     * The playground scope, which allows to retrieve the [PGEnv]
     * and defers exception handling to [exceptionHandler]
     */
    private val playgroundScope: CoroutineScope

    val availableCmds: List<PGCmd> =
        listOf(
            NewSwitch,
            RmNode,
            NewFlow,
            RmFlow,
            NewLink,
            RmLink,
            EnReport,
            ShowFlows,
            NodeInfo,
            ShowSnapshot,
            Export,
            AdvanceTime,
            Quit,
        )

    init {
        val network: Network =
            if (file.exists()) {
                val jsonReader = Json { ignoreUnknownKeys = true }
                val networkSpecs: Specs<Network> = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
                val network = networkSpecs.build()
                LOG.info("starting network built from file ${file.name} ${network.fmtNodes()}")
                network
            } else {
                println("file not provided or invalid, falling back to an empty custom network...")
                CustomNetwork()
            }

        val energyRecorder = NetEnRecorder(network)
        env = PGEnv(
            network = network,
            energyRecorder = energyRecorder,
            pgTimeSource = PGTimeSource(Instant.now())
        )

        playgroundScope =
            CoroutineScope(
                Dispatchers.Default +
                    CoroutineName("network playground") +
                    supervisorJob +
                    exceptionHandler +
                    env +
                    network.validator
            )
    }

    override fun run() {
        runBlocking {
            env.network.launch()

            with(playgroundScope) {
                while (true) {
                    // Get user input.
                    val input: String = readlnOrNull() ?: continue

                    // When `input` matches a cmd regex, that command is executed.
                    whenMatch(input) {
                        availableCmds.forEachWith {
                            regex { exec(this@regex) }
                        }
                        otherwise { LOG.error("unrecognized command") }
                    }
                }
            }
        }
    }

    companion object {
        val LOG by logger()
    }
}

private fun <T> Iterable<T>.forEachWith(block: T.() -> Unit) {
    forEach { with(it) { block() } }
}
