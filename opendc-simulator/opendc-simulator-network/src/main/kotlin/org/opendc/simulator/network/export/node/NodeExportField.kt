//package org.opendc.simulator.network.export.node
//
//import org.apache.parquet.io.api.Binary
//import org.apache.parquet.io.api.RecordConsumer
//import org.apache.parquet.schema.LogicalTypeAnnotation
//import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit.MILLIS
//import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY
//import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
//import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32
//import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
//import org.apache.parquet.schema.Type
//import org.apache.parquet.schema.Types
//import org.opendc.simulator.network.api.snapshots.NodeSnapshot
//
//public abstract class NodeExportField {
//    internal abstract val fld: Type
//    internal val fldName: String get() = fld.name
//    protected abstract fun RecordConsumer.addValue(snapshot: NodeSnapshot)
//
//    internal fun RecordConsumer.writeField(snapshot: NodeSnapshot, index: Int) {
//        startField(fldName, index)
//        addValue(snapshot)
//        endField(fldName, index)
//    }
//
//    public companion object {
//        public val NODE_ID: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(INT64)
//                .`as`(LogicalTypeAnnotation.timestampType(true, MILLIS))
//                .named("node_id")
//
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addLong(snapshot.nodeId)
//            }
//        }
//        public val TIMESTAMP: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(INT64).named("timestamp")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addLong(snapshot.instant.toEpochMilli())
//            }
//        }
//        public val NAME: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(BINARY).named("node_name")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addBinary(Binary.fromString("unknown")) // TODO: implement
//            }
//        }
//        public val FLOWS_IN: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(INT32).named("num_incoming_flows")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addInteger(snapshot.numIncomingFlows)
//            }
//        }
//        public val FLOWS_OUT: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(INT32).named("num_outgoing_flows")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addInteger(snapshot.numOutgoingFlows)
//            }
//        }
//        public val GEN_FLOWS: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(INT32).named("num_flows_being_generated")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addInteger(snapshot.numGeneratedFlows)
//            }
//        }
//        public val CONS_FLOWS: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(INT32).named("num_flows_being_consumed")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addInteger(snapshot.numConsumingFlows)
//            }
//        }
//        public val MIN_F_TPUT: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("curr_min_flow_throughput[%]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.currMinFlowTputPerc ?: .0)
//            }
//        }
//        public val MAX_F_TPUT: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("curr_max_flow_throughput[%]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.currMaxFlowTputPerc ?: .0)
//            }
//        }
//        public val AVRG_F_TPUT: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("curr_average_flow_throughput[%]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.currAvrgFlowTputPerc)
//            }
//        }
//        public val TPUT: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("curr_throughput_among_all_flows[Mbps]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.currNodeTputAllFlows.toMbps())
//            }
//        }
//        public val TPUT_PERC: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("curr_throughput_among_all_flows[%]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.currNodeTputPercAllFlows)
//            }
//        }
//        public val CURR_PWR_USE: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("curr_pwr_use[Watts]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.currPwrUse.toWatts())
//            }
//        }
//        public val AVRG_PWR_USE: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("avrg_pwr_use_over_time[Watts]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.avrgPwrUseOverTime.toWatts())
//            }
//        }
//        public val EN_CONSUMPT: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("tot_energy_consumption[Wh]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.totEnConsumed.toWh())
//            }
//        }
//        public val MAX_PORT_SPEED: NodeExportField = object : NodeExportField() {
//            override val fld: Type = Types.required(DOUBLE).named("max_port_speed[Mbps]")
//            override fun RecordConsumer.addValue(snapshot: NodeSnapshot) {
//                addDouble(snapshot.maxPortSpeed.toMbps())
//            }
//        }
//        public val ALL_NET_FIELDS: Set<NodeExportField> = setOf(
//            NODE_ID, TIMESTAMP, NAME, FLOWS_IN, FLOWS_OUT, GEN_FLOWS,
//            CONS_FLOWS, MIN_F_TPUT, MAX_F_TPUT, AVRG_F_TPUT, TPUT, TPUT_PERC,
//            CURR_PWR_USE, AVRG_PWR_USE, EN_CONSUMPT, MAX_PORT_SPEED
//        )
//    }
//}