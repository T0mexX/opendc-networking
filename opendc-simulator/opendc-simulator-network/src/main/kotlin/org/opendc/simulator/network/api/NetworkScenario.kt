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

package org.opendc.simulator.network.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.common.logger.infoNewLine
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.input.readNetworkWl
import org.opendc.simulator.network.utils.logger
import org.opendc.trace.util.parquet.exporter.Exporter
import java.io.File
import java.util.UUID
import javax.naming.OperationNotSupportedException

/**
 * Represents a runnable scenario which simulates workload [wl]
 * on the network defined by [networkSpecs] and exports results
 * in parquet format based on [exportConfig].
 *
 * @property[networkSpecs]      the specifications of the network on which to run the [wl].
 * @property[wl]                the network workload to simulate.
 * @property[exportConfig]      the settings that determine what, where will be exported and with what frequency.
 * @property[virtualMapping]    flag determining if virtual mapping is to be performed.
 * If virtual mapping is not performed than the node ids in the [wl] need to reflect exactly those in [networkSpecs].
 * If virtual mapping is performed than the nodes in the [wl] are mapped onto physical nodes in the network defined by [networkSpecs]
 */
@Serializable(with = NetworkScenario.NetScenarioSerializer::class)
public data class NetworkScenario(
    val networkSpecs: Specs<Network>,
    val wl: SimNetWorkload,
    val exportConfig: NetworkExportConfig? = null,
    val virtualMapping: Boolean = true,
) : Runnable {
    init {
        exportConfig?.let { LOG.infoNewLine(it.fmt()) }
    }

    override fun run() {
        LOG.infoNewLine(wl.fmt())
        RunInstance()
    }

    private inner class RunInstance(
        private val runName: String = "unnamed-run-${UUID.randomUUID().toString().substring(0, 4)}",
    ) : AutoCloseable {
        val network: Network = networkSpecs.build()
        val netController: NetworkController = NetworkController(network)
        val runWl: SimNetWorkload = wl.copy()
        val pb: ProgressBar = getProgressBar()
        var exportDeadline: Time?
        val networkExporter: Exporter<NetworkSnapshot>?
        val nodeExporter: Exporter<NodeSnapshot>?

        init {
            with(exportConfig) {
                this ?: let {
                    exportDeadline = null
                    networkExporter = null
                    nodeExporter = null
                    return@with
                }

                exportDeadline = exportInterval?.plus(Time.ofInstantFromEpoch(runWl.startInstant))

                // NetworkExportConfig serialization guarantees that
                // outputFolder exists (if config are not null).
                val runOutputFolder =
                    outputFolder.let {
                        File(it.absolutePath, runName).also { f -> check(f.mkdirs()) }
                    }

                networkExporter =
                    networkExportColumns.let { columns ->
                        if (columns.isEmpty() || exportDeadline == null) {
                            null
                        } else {
                            Exporter(
                                outputFile = File(runOutputFolder.absolutePath, "network.parquet"),
                                columns = columns,
                            )
                        }
                    }

                nodeExporter =
                    exportConfig?.nodeExportColumn?.let { columns ->
                        if (columns.isEmpty() || exportDeadline == null) {
                            null
                        } else {
                            Exporter(
                                outputFile = File(runOutputFolder.absolutePath, "node.parquet"),
                                columns = columns,
                            )
                        }
                    }
            }

            if (virtualMapping) runWl.performVirtualMappingOn(netController)
            // Sets controller time to the instant of the first network event.
            netController.instantSrc.setInternalTime(
                Time.ofInstantFromEpoch(runWl.startInstant),
            )

            netController.execWl(runWl)
        }

        private fun NetworkController.execWl(runWl: SimNetWorkload) =
            runBlocking(network.validator) {
                delay(1000)
                with(runWl) {
                    while (runWl.hasNext()) {
                        val nextDeadline = nextDeadline()
                        // Executes all network events up until `nextDeadline`
                        // included (with all events with that deadline).
                        pb.stepBy(execUntil(nextDeadline))
                        network.awaitStability()

                        exportIfNeeded()
                    }
                }

                pb.refresh()
                println()
                LOG.infoNewLine(energyRecorder.fmt())
                this@RunInstance.close()
            }

        private fun getProgressBar(): ProgressBar =
            ProgressBarBuilder()
                .setInitialMax(wl.size.toLong())
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Simulating network...")
                .build()

        private fun nextDeadline(): Time = exportDeadline?.min(runWl.peek().deadline) ?: runWl.peek().deadline

        private fun exportIfNeeded() {
            // Non-mutable for smart cast.
            val exportDeadline = exportDeadline ?: return
            val currTime = netController.instantSrc.time

            // If not yet time to export then return.
            if (exportDeadline approxLarger currTime) return
            // Check export deadline not passed yet.
            check(exportDeadline approx currTime)

            // Write network snapshot to output file.
            networkExporter?.write(netController.snapshot())

            // Write each node's snapshot to output file.
            network.nodesById.values.forEach {
                if (it.id == INTERNET_ID) return@forEach
                nodeExporter?.write(
                    it.snapshot(
                        instant = netController.currentInstant,
//                        withStableNetwork = network,
                    ),
                )
            }

            this.exportDeadline = exportConfig?.exportInterval!! + exportDeadline
        }

        override fun close() {
            netController.close()
            networkExporter?.close()
            nodeExporter?.close()
        }
    }

    internal class NetScenarioSerializer: KSerializer<NetworkScenario> {
        @Serializable
        private data class NetScenarioSurrogate(
            val networkSpecsPath: String,
            val wlPath: String,
            val exportConfig: NetworkExportConfig? = null,
            val virtualMapping: Boolean = true,
        )

        private val surrogateSerial: KSerializer<NetScenarioSurrogate> = kotlinx.serialization.serializer()

        override val descriptor: SerialDescriptor = serialDescriptor<NetScenarioSurrogate>()

        override fun deserialize(decoder: Decoder): NetworkScenario {
            val surrogate: NetScenarioSurrogate = decoder.decodeSerializableValue(surrogateSerial)

            return NetworkScenario(
                networkSpecs = Specs.fromFile(surrogate.networkSpecsPath),
                wl = readNetworkWl(surrogate.wlPath),
                exportConfig = surrogate.exportConfig,
                virtualMapping = surrogate.virtualMapping,
            )
        }

        override fun serialize(encoder: Encoder, value: NetworkScenario) {
            throw OperationNotSupportedException()
        }
    }

    internal companion object {
        private val LOG by logger()
    }
}


