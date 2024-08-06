package org.opendc.simulator.network.export.network

import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.opendc.simulator.network.export.ExportField
import org.apache.parquet.schema.Type
import org.apache.parquet.schema.Types
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32
import org.opendc.simulator.network.api.NetworkSnapshot


public val TIMESTAMP: ExportField<NetworkSnapshot> = object : ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT64).named("timestamp")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addLong(exportable.instant.toEpochMilli())
    }
}
public val NUM_FLOWS: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("num_flows")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.numActiveFlows)
    }
}
public val NUM_NODES: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("num__nodes")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.numNodes)
    }
}
public val NUM_HOST_NODES: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("num_host_nodes")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.numHostNodes)
    }
}
public val NUM_ACTIVE_HOST_NODES: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(INT32).named("num_active_host_nodes")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addInteger(exportable.claimedHostNodes)
    }
}
public val AVRG_TPUT_PERC: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("avrg_tput[%]")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.avrgThroughputPerc)
    }
}
public val TOT_TPUT_PERC: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("tot_tput[%]")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.totThroughputPerc)
    }
}
public val CURR_PWR_USE: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("curr_pwr_use[KW]")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.currPwrUse.toKWatts())
    }
}
public val AVRG_PWR_USE: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("avrg_pwr_use[KW]")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.avrgPwrUseOverTime.toKWatts())
    }
}
public val EN_CONSUMED: ExportField<NetworkSnapshot> = object: ExportField<NetworkSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("tot_en_consumed[KWh]")
    override fun RecordConsumer.addValue(exportable: NetworkSnapshot) {
        addDouble(exportable.totEnergyConsumed.toKWh())
    }
}


