package org.opendc.simulator.network.input

import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
import org.apache.parquet.schema.Types
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.input.ImprintsToWlConverter.Companion.toWl
import org.opendc.trace.util.parquet.LocalParquetReader
import java.io.File
import java.lang.RuntimeException

public fun readNetworkWl(path: String): SimNetWorkload = readNetworkWl(File(path))

public fun readNetworkWl(file: File): SimNetWorkload =
    try {
        val reader = LocalParquetReader(file = file, readSupport = NetEventReadSupp())
        val imprints: List<NetEventImprint>  = buildList {
            while (true) {
                reader.read()?.let { add(it) } ?: break
            }
        }

        imprints.toWl()
    } catch (e: Exception) {
        // kotlin version does not compile for some reason.
        throw java.lang.RuntimeException("unable to read network workload from $file.", e)
    }

/**
 * The timestamp when the network event occurs.
 */
internal val TIMESTAMP_FIELD =
    Types
        .required(INT64)
        .`as`(LogicalTypeAnnotation.timestampType(false, LogicalTypeAnnotation.TimeUnit.MILLIS))
        .named("timestamp")

/**
 * The id of the transmitter node. The omission of this field is interpreted
 * as a flow coming from outside the datacenter network.
 * Keep in mind that if virtual mapping is used (workload node ids are mapped to topology node ids),
 * then all node ids are interpreted as host ids.
 */
internal val TRANSMITTER_ID_FIELD =
    Types
        .optional(INT64)
        .named("transmitter_id")

/**
 * The absence of destination for [NET_TX_FIELD] (transmission) is interpreted
 * as an inter-datacenter transmission (to internet).
 */
internal val DEST_ID_FIELD =
    Types
        .optional(INT64)
        .named("dest_id")

/**
 * The transmission data-rate in Kbps (from [TRANSMITTER_ID_FIELD] to [DEST_ID_FIELD]).
 */
internal val NET_TX_FIELD =
    Types
        .required(DOUBLE)
        .named("net_tx")

/**
 * The absence of this field means only 1 flow between 2 nodes can be active at the same time,
 * and each entry in the workload (with the same transmitter and destination) is interpreted as an update to that flow.
 */
internal val FLOW_ID_FIELD =
    Types
        .required(INT64)
        .named("flow_id")

/**
 * If duration is defined a FlowStop event is automatically added to the workload,
 * otherwise a trace entry for the flow stop is excepted (and if absent the
 * flow will be active for the whole simulation).
 */
internal val DURATION_FIELD =
    Types
        .optional(INT64)
        .named("duration")


/**
 * Columns that need to be in the file schema (it does not
 * mean that the fields are required for each entry).
 */
internal val mandatoryColumns = listOf(TRANSMITTER_ID_FIELD, NET_TX_FIELD, TIMESTAMP_FIELD)


/**
 * Columns that do not need to be in the file schema (it does not
 * mean that the fields are optional if the column is in the schema).
 */
internal val notMandatoryColumns = listOf(DEST_ID_FIELD, FLOW_ID_FIELD, DURATION_FIELD)
