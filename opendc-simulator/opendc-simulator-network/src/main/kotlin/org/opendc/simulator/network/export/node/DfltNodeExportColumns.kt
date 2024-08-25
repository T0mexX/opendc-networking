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

package org.opendc.simulator.network.export.node

import org.apache.parquet.io.api.Binary
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64
import org.apache.parquet.schema.Types
import org.opendc.simulator.network.api.snapshots.NodeSnapshot
import org.opendc.trace.util.parquet.exporter.ExportColumn

public object DfltNodeExportColumns {
    public val TIMESTAMP: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(INT64).named("timestamp_absolute"),
            //        field = Types.required(INT64)
            //            .`as`(LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MILLIS))
            //            .named("a_timestamp")
        ) { it.instant.toEpochMilli() }

    public val NODE_ID: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(INT64).named("node_id"),
        ) { it.nodeId }

    public val NAME: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field =
                Types.required(BINARY)
                    .`as`(LogicalTypeAnnotation.stringType())
                    .named("node_name"),
        ) { Binary.fromString("unknown") } // TODO: implement name

    public val FLOWS_IN: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("incoming_flows"),
        ) { it.numIncomingFlows }

    public val FLOWS_OUT: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("outgoing_flows"),
        ) { it.numOutgoingFlows }

    public val GEN_FLOWS: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("flows_being_generated"),
        ) { it.numGeneratingFlows }

    public val CONS_FLOWS: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(INT32).named("flows_being_consumed"),
        ) { it.numConsumedFlows }

    public val MIN_F_TPUT: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("min_flow_throughput_ratio"),
        ) { it.currMinFlowTputPerc?.toRatio() }

    public val MAX_F_TPUT: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("max_flow_throughput_ratio"),
        ) { it.currMaxFlowTputPerc?.toRatio() }

    public val AVRG_F_TPUT: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("avg_flow_throughput_ratio"),
        ) { it.currAvrgFlowTputPerc?.toRatio() }

    public val TPUT: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(DOUBLE).named("node_throughput_Mbps"),
        ) { it.currNodeTputAllFlows.toMbps() }

    public val TPUT_PERC: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.optional(DOUBLE).named("node_throughput_ratio"),
        ) { it.currNodeTputPercAllFlows?.toRatio() }

    public val CURR_PWR_USE: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(DOUBLE).named("pwr_draw_Watts"),
        ) { it.currPwrUse.toWatts() }

    public val EN_CONSUMPT: ExportColumn<NodeSnapshot> =
        ExportColumn(
            field = Types.required(DOUBLE).named("energy_consumption_joule"),
        ) { it.totEnConsumed.toJoule() }
}
