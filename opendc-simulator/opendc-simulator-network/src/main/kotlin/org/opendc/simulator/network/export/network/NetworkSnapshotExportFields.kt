package org.opendc.simulator.network.export.network

import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.opendc.simulator.network.export.ExportField
import org.apache.parquet.schema.Type
import org.apache.parquet.schema.Types
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot

/*
NOTE: Parquet supports only column names with alphanumerical characters and underscores.
 */

public val TIMESTAMP: ExportField<NetworkSnapshot> = object : ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT64)
        .`as`(LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MILLIS))
        .named("a_timestamp")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addLong(exportable.instant.toEpochMilli())
    }
}
public val NUM_FLOWS: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("b_num_flows")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.numActiveFlows)
    }
}
public val NUM_NODES: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("c_num_nodes")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.numNodes)
    }
}
public val NUM_HOST_NODES: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("d_num_host_nodes")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.numHostNodes)
    }
}
public val NUM_ACTIVE_HOST_NODES: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("e_num_active_host_nodes")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.claimedHostNodes)
    }
}
public val AVRG_TPUT_PERC: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("f_avrg_tput_ratio")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.avrgTputPerc)
    }
}
public val TOT_TPUT_PERC: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("g_tot_tput_ratio")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.totTputPerc)
    }
}
public val CURR_PWR_USE: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("h_curr_pwr_use_KW") // parquet does not allo
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.currPwrUse.toKWatts())
    }
}
public val AVRG_PWR_USE: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("i_avrg_pwr_use_KW")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.avrgPwrUseOverTime.toKWatts())
    }
}
public val EN_CONSUMED: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("l_tot_en_consumed_KWh")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.totEnConsumed.toKWh())
    }
}

public val ALL_NET_FIELDS: Set<ExportField<NetworkSnapshot>> =
    setOf(
        TIMESTAMP, NUM_FLOWS, NUM_NODES, NUM_HOST_NODES, NUM_ACTIVE_HOST_NODES,
        AVRG_TPUT_PERC, TOT_TPUT_PERC, CURR_PWR_USE, AVRG_PWR_USE, EN_CONSUMED
    )


