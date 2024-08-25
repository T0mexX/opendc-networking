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
    private val runName: String = "unnamed-run-${UUID.randomUUID().toString().substring(0, 4)}",
): AutoCloseable {
    private var startTime: Time? = config.startTime
    internal var nextExportDeadline: Time? = startTime?.plus(config.exportInterval)
        private set
    private val networkExporter: Exporter<NetworkSnapshot>?
    private val nodeExporter: Exporter<NodeSnapshot>?

    init {

        with(config) {
            // NetworkExportConfig serialization guarantees that
            // outputFolder exists (if config are not null).
            val runOutputFolder =
                outputFolder.let {
                    File(it.absolutePath, runName).also { f -> check(f.mkdirs()) }
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
                nodeExportColumn.let { columns ->
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

    internal fun NetworkController.timeUntilExport() = getNextDeadline() - instantSrc.time

    internal fun NetworkController.exportIfNeeded() {
        // Non-mutable for smart cast.
        val exportDeadline = getNextDeadline()
        val currTime = instantSrc.time

        // If not yet time to export then return.
        if (exportDeadline approxLarger currTime) return
        // Check export deadline not passed yet.
        check(exportDeadline approx currTime)

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

            LOG.warn("start time for network export was set automatically on first " +
                "invocation of export functions. The current time might not be the desired start time."
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
