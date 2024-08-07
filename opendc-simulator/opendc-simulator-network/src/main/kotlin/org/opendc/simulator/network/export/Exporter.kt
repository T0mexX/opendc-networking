package org.opendc.simulator.network.export

import org.apache.hadoop.conf.Configuration
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.api.WriteSupport
import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.Types
import org.opendc.trace.util.parquet.LocalParquetWriter
import org.opendc.trace.util.parquet.ParquetDataWriter
import java.io.File


internal class Exporter<T: Exportable<T>>(
    outputFile: File,
    fields: Collection<ExportField<T>> = emptySet()
): ParquetDataWriter<T>(
    path = outputFile,
    writeSupport = writeSuppFor(fields.toSet())
) {
    constructor(outputFile: File, vararg fields: ExportField<T> = emptyArray())
        : this(outputFile = outputFile, fields = fields.toSet())
    constructor(outputPath: String, vararg fields: ExportField<T> = emptyArray())
        : this(outputFile = File(outputPath), fields = fields.toSet())
    constructor(outputPath: String, fields: Set<ExportField<T>> = emptySet())
        : this(outputFile = File(outputPath), fields = fields.toSet())
}

private fun <T: Exportable<T>> writeSuppFor(fields: Set<ExportField<T>>): WriteSupport<T> =
    object: WriteSupport<T>() {
        private lateinit var cons: RecordConsumer

        private val schema: MessageType =
            Types
                .buildMessage()
                .addFields(*fields.map { it.fld }.toTypedArray())
                .named("bo")

//        init {
//            println(schema)
//            println(Types.buildMessage().addFields(Types.required(PrimitiveType.PrimitiveTypeName.INT64).named("ciao")).named("bo"))
//        }

        override fun init(configuration: Configuration): WriteContext =
            WriteContext(schema, emptyMap())

        override fun prepareForWrite(recordConsumer: RecordConsumer) {
            cons = recordConsumer
        }

        override fun write(record: T) {
            cons.startMessage()
            fields.forEachIndexed { idx, field ->
                with(field) { cons.writeField(exportable = record, index = idx) }
            }
            cons.endMessage()
        }
    }

