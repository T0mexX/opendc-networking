package org.opendc.simulator.network.export.node

import org.apache.parquet.io.api.Binary
import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.simulator.network.export.ExportField
import org.apache.parquet.schema.Type
import org.apache.parquet.schema.Types
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit.MILLIS
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64

public val TIMESTAMP: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(INT64)
        .`as`(LogicalTypeAnnotation.timestampType(true, MILLIS))
        .named("a_timestamp")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addLong(exportable.instant.toEpochMilli())
    }
}
public val NODE_ID: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(INT64).named("b_node_id")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addLong(exportable.nodeId)
    }
}
public val NAME: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(BINARY)
        .`as`(LogicalTypeAnnotation.stringType())
        .named("c_node_name")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addBinary(Binary.fromString("unknown")) // TODO: implement
    }
}
public val FLOWS_IN: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(INT32).named("d_num_incoming_flows")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addInteger(exportable.numIncomingFlows)
    }
}
public val FLOWS_OUT: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(INT32).named("e_num_outgoing_flows")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addInteger(exportable.numOutgoingFlows)
    }
}
public val GEN_FLOWS: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(INT32).named("f_num_flows_being_generated")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addInteger(exportable.numGeneratedFlows)
    }
}
public val CONS_FLOWS: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(INT32).named("g_num_flows_being_consumed")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addInteger(exportable.numConsumedFlows)
    }
}
public val MIN_F_TPUT: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("h_curr_min_flow_throughput_ratio")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.currMinFlowTputPerc ?: .0)
    }
}
public val MAX_F_TPUT: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("i_curr_max_flow_throughput_ratio")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.currMaxFlowTputPerc ?: .0)
    }
}
public val AVRG_F_TPUT: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("l_curr_average_flow_throughput_ratio")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.currAvrgFlowTputPerc)
    }
}
public val TPUT: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("m_curr_throughput_among_all_flows_Mbps")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.currNodeTputAllFlows.toMbps())
    }
}
public val TPUT_PERC: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("n_curr_throughput_among_all_flows_ratio")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.currNodeTputPercAllFlows)
    }
}
public val CURR_PWR_USE: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("o_curr_pwr_use_Watts")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.currPwrUse.toWatts())
    }
}
public val AVRG_PWR_USE: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("p_avrg_pwr_use_over_time_Watts")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.avrgPwrUseOverTime.toWatts())
    }
}
public val EN_CONSUMPT: ExportField<NodeSnapshot> = object : ExportField<NodeSnapshot>() {
    override val fld: Type = Types.required(DOUBLE).named("q_tot_energy_consumption_Wh")
    override fun RecordConsumer.addValue(exportable: NodeSnapshot) {
        addDouble(exportable.totEnConsumed.toWh())
    }
}
public val ALL_NODE_FIELDS: Set<ExportField<NodeSnapshot>> = setOf(
    NODE_ID, TIMESTAMP, NAME, FLOWS_IN, FLOWS_OUT, GEN_FLOWS,
    CONS_FLOWS, MIN_F_TPUT, MAX_F_TPUT, AVRG_F_TPUT, TPUT, TPUT_PERC,
    CURR_PWR_USE, AVRG_PWR_USE, EN_CONSUMPT
)
