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

package org.opendc.simulator.network.export

import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.utils.logger
import org.opendc.trace.util.parquet.exporter.Exporter
import java.io.File
import java.util.UUID

internal class NetExportHandler(
    private val config: NetworkExportConfig,
): AutoCloseable {
    private var startTime: Time? = config.startTime
    internal var nextExportDeadline: Time? = startTime?.plus(config.exportInterval)
        private set
    private val networkExporter: Exporter<NetworkSnapshot>?
    private val nodeExporter: Exporter<NodeSnapshot>?

    init {
        require(config.outputFolder != null)

        with(config) {
            // NetworkExportConfig serialization guarantees that
            // outputFolder exists (if config are not null).
            val runOutputFolder =
                outputFolder.let {
                    File(it!!.absolutePath, "networking").also { f ->

                        check(f.mkdirs() || f.exists()) {
                            "unable to create directory (and parents) for path ${f.absolutePath}"
                        }
                    }
                }

            networkExporter =
                networkExportColumns.let { columns ->
                    if (columns.isEmpty()) {
                        null
                    } else {
                        Exporter(
                            outputFile = File(runOutputFolder.absolutePath, "network.parquet"),
                            columns = columns,
                        )
                    }
                }

            nodeExporter =
                nodeExportColumns.let { columns ->
                    if (columns.isEmpty()) {
                        null
                    } else {
                        Exporter(
                            outputFile = File(runOutputFolder.absolutePath, "node.parquet"),
                            columns = columns,
                        )
                    }
                }
        }
    }

    internal fun NetworkController.timeUntilExport() = getNextDeadline() - lastUpdate

    internal fun NetworkController.exportIfNeeded() {
        // Non-mutable for smart cast.
        val exportDeadline = getNextDeadline()

        // If not yet time to export then return.
        if (exportDeadline approxLarger lastUpdate) return
        // Check export deadline not passed yet.
        check(exportDeadline approx lastUpdate)

        // Write network snapshot to output file.
        networkExporter?.write(snapshot())

        // Write each node's snapshot to output file.
        network.nodesById.values.forEach {
            if (it.id == INTERNET_ID) return@forEach
            nodeExporter?.write(
                it.snapshot(
                    instant = currentInstant,
//                        withStableNetwork = network,
                ),
            )
        }

        nextExportDeadline = exportDeadline + config.exportInterval
    }

    private fun NetworkController.getNextDeadline(): Time {
        val nextDeadline = nextExportDeadline

        return nextDeadline ?: let {
            startTime = instantSrc.time
            nextExportDeadline = instantSrc.time + config.exportInterval

            LOG.warn(
                "start time for network export was set automatically on first " +
                    "invocation of export functions. The current time might not be the desired start time.",
            )

            nextExportDeadline!!
        }
    }

    override fun close() {
        nodeExporter?.close()
        networkExporter?.close()
    }

    private companion object {
        val LOG by logger()
    }
}
