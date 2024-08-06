//package org.opendc.simulator.network.export.node
//
//import org.apache.hadoop.conf.Configuration
//import org.apache.parquet.hadoop.api.WriteSupport
//import org.apache.parquet.io.api.RecordConsumer
//import org.apache.parquet.schema.MessageType
//import org.apache.parquet.schema.Types
//import org.opendc.simulator.network.api.NodeSnapshot
//import org.opendc.trace.util.parquet.ParquetDataWriter
//import java.io.File
//
//internal class ExportWriter(
//    file: File,
//    vararg fields: NodeExportField = arrayOf()
//): ParquetDataWriter<NodeSnapshot>(
//    path = file,
//    writeSupport = nodeWriteSuppFor(if (fields.isEmpty()) NodeExportField.ALL else fields.toSet())
//)
//
//private fun nodeWriteSuppFor(fields: Set<NodeExportField>): WriteSupport<NodeSnapshot> =
//    object: WriteSupport<NodeSnapshot>() {
//        private lateinit var cons: RecordConsumer
//
//        private val schema: MessageType =
//            Types
//                .buildMessage()
//                .addFields(*fields.map { it.fld }.toTypedArray())
//                .named("node-output-schema")
//
//        override fun init(configuration: Configuration?): WriteContext =
//            WriteContext(schema, emptyMap())
//
//        override fun prepareForWrite(recordConsumer: RecordConsumer) {
//            cons = recordConsumer
//        }
//
//        override fun write(record: NodeSnapshot) {
//            fields.forEachIndexed { idx, field ->
//                with(field) { cons.writeField(snapshot = record, index = idx) }
//            }
//        }
//    }
//
