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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.opendc.common.logger.logger
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.export.network.DfltNetworkExportColumns
import org.opendc.simulator.network.export.node.DfltNodeExportColumns
import org.opendc.trace.util.parquet.exporter.ColListSerializer
import org.opendc.trace.util.parquet.exporter.ExportColumn
import org.opendc.trace.util.parquet.exporter.Exportable
import org.opendc.trace.util.parquet.exporter.columnSerializer
import java.io.File

/**
 * Aggregates the necessary settings to personalize the output
 * parquet files for network simulations.
 *
 * @param[networkExportColumns]     the columns that will be included in the `network.parquet` raw output file.
 * @param[nodeExportColumn]         the columns that will be included in the `node.parquet` raw output file.
 * @param[exportInterval]           the time interval between exports. If it is handled externally, this param should be `null`.
 */
@Serializable(with = NetworkExportConfig.Companion.NetExpConfigSerializer::class)
public data class NetworkExportConfig(
    val networkExportColumns: List<ExportColumn<NetworkSnapshot>>,
    val nodeExportColumn: List<ExportColumn<NodeSnapshot>>,
    val outputFolder: File,
    val exportInterval: Time,
    val startTime: Time? = null,
) {
    /**
     * @return formatted string representing the export config.
     */
    public fun fmt(): String =
        """
        | === NETWORK EXPORT CONFIG ===
        | Network columns  : ${networkExportColumns.map { it.name }.toString().trim('[', ']')}
        | Node columns     : ${nodeExportColumn.map { it.name }.toString().trim('[', ']')}
        | Export interval  : $exportInterval
        """.trimIndent()

    public companion object {
        internal val LOG by logger()

        /**
         * Force the jvm to load the default [ExportColumn]s relevant to network export,
         * so that they are available for deserialization.
         */
        public fun loadDfltColumns() {
            DfltNetworkExportColumns
            DfltNodeExportColumns
        }

        /**
         * A runtime [KSerializer] is needed for reasons explained in [columnSerializer] docs.
         *
         * This serializer makes use of reified column serializers for the 2 properties.
         */
        internal object NetExpConfigSerializer : KSerializer<NetworkExportConfig> {
            private val nullableTimeSerializer: KSerializer<Time?> = kotlinx.serialization.serializer<Time?>()

            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("org.opendc.compute.telemetry.export.parquet.ComputeExportConfig") {
                    element(
                        "networkExportColumns",
                        ListSerializer(columnSerializer<NetworkSnapshot>()).descriptor,
                    )
                    element(
                        "nodeExportColumns",
                        ListSerializer(columnSerializer<NodeSnapshot>()).descriptor,
                    )
                    element(
                        "outputFolder",
                        serialDescriptor<String>()
                    )
                    element(
                        "exportInterval",
                        nullableTimeSerializer.descriptor,
                    )
                }

            override fun deserialize(decoder: Decoder): NetworkExportConfig {
                // Basically a recursive call with a JsonDecoder.
                val jsonDec =
                    (decoder as? JsonDecoder) ?: let {
                        return Json.decodeFromString(decoder.decodeString().trim('"'))
                    }

                // Loads the default columns so that they are available for deserialization.
                loadDfltColumns()
                val elem = jsonDec.decodeJsonElement().jsonObject

                val outputFolder = File(elem["outputFolder"].toString().trim('"'))
                check(outputFolder.exists().not() || outputFolder.isDirectory)
                outputFolder.mkdirs()

                return NetworkExportConfig(
                    networkExportColumns = elem["networkExportColumns"].toFieldList(),
                    nodeExportColumn = elem["nodeExportColumns"].toFieldList(),
                    outputFolder = outputFolder,
                    exportInterval = Json.decodeFromString(elem["exportInterval"].toString().trim('"'))
                )
            }

            override fun serialize(
                encoder: Encoder,
                value: NetworkExportConfig,
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private inline fun <reified T : Exportable> JsonElement?.toFieldList(): List<ExportColumn<T>> =
    this?.let {
        json.decodeFromJsonElement(ColListSerializer(columnSerializer<T>()), it)
    }?.ifEmpty {
        NetworkExportConfig.LOG.warn(
            "deserialized list of export columns for exportable ${T::class.simpleName} " +
                "produced empty list, falling back to all loaded columns",
        )
        ExportColumn.getAllLoadedColumns()
    } ?: ExportColumn.getAllLoadedColumns()
