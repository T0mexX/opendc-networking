package org.opendc.simulator.network.export

import org.apache.hadoop.conf.Configuration
import org.apache.parquet.hadoop.api.WriteSupport
import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.Types
import org.opendc.trace.util.parquet.ParquetDataWriter
import java.io.File


public class Exporter<T: Exportable<T>> @PublishedApi internal constructor(
    outputFile: File,
    writeSupp: WriteSupport<T>
): ParquetDataWriter<T>(
    path = outputFile,
    writeSupport = writeSupp
) {
    public companion object {
        public inline operator fun <reified T: Exportable<T>> invoke(outputFile: File, vararg fields: ExportField<T> = emptyArray()): Exporter<T> =
            Exporter(outputFile = outputFile, writeSupp = writeSuppFor(fields.toSet()))


        public inline operator fun <reified T: Exportable<T>> invoke(outputFile: File, fields: Set<ExportField<T>> = emptySet()): Exporter<T> =
            Exporter(outputFile = outputFile, writeSupp = writeSuppFor(fields.toSet()))
    }
}

@PublishedApi
internal inline fun <reified T: Exportable<T>> writeSuppFor(fields: Set<ExportField<T>>): WriteSupport<T> =
    object: WriteSupport<T>() {
        private lateinit var cons: RecordConsumer

        private val schema: MessageType =
            Types
                .buildMessage()
                .addFields(*fields.map { it.fld }.toTypedArray())
                .named("${T::class.simpleName}_schema")

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

