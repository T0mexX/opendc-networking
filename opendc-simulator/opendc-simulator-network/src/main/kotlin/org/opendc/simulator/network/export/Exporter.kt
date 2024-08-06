package org.opendc.simulator.network.export

import org.apache.hadoop.conf.Configuration
import org.apache.parquet.hadoop.api.WriteSupport
import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.Types
import org.opendc.trace.util.parquet.ParquetDataWriter
import java.io.File


internal class Exporter<T: Exportable<T>>(
    outputFile: File,
    vararg fields: ExportField<T> = emptyArray()
): ParquetDataWriter<T>(
    path = outputFile,
    writeSupport = writeSuppFor(fields.toSet())
)

private fun <T: Exportable<T>> writeSuppFor(fields: Set<ExportField<T>>): WriteSupport<T> =
    object: WriteSupport<T>() {
        private lateinit var cons: RecordConsumer

        private val schema: MessageType =
            Types
                .buildMessage()
                .addFields(*fields.map { it.fld }.toTypedArray())
                .named("node-output-schema")

        override fun init(configuration: Configuration?): WriteContext =
            WriteContext(schema, emptyMap())

        override fun prepareForWrite(recordConsumer: RecordConsumer) {
            cons = recordConsumer
        }

        override fun write(record: T) {
            fields.forEachIndexed { idx, field ->
                with(field) { cons.writeField(exportable = record, index = idx) }
            }
        }
    }

