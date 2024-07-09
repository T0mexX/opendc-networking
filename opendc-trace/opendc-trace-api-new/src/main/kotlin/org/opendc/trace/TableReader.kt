package org.opendc.trace

import java.io.File
import java.nio.file.Path

public interface TableReader {
    public fun nextLine(): Boolean

    public fun <O, P: Any> addColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<O>,
        process: (O) -> P = ColumnReader.automaticConversion(),
        postProcess: (P) -> Unit = {}
    ): ColumnReader<O, P>?

    public fun <O, P: Any> withColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<O>,
        process: (O) -> P = ColumnReader.automaticConversion(),
        postProcess: (P) -> Unit = {}
    ): TableReader? {
        addColumnReader(
            name = name,
            columnType = columnType,
            process = process,
            postProcess = postProcess
        ) ?: return null

        return this
    }



    private enum class Ext {
        CSV;
    }

    public companion object {
        public fun genericReaderFor(file: File): TableReader =
            when (file.extension) {
                "csv" -> CSVTableReader(file)
                else -> throw IllegalArgumentException("unsupported file extension")
            }

        public fun genericReaderFor(path: Path): TableReader =
            genericReaderFor(path.toFile())

        public fun genericReaderFor(path: String): TableReader =
            genericReaderFor(File(path))
    }
}
