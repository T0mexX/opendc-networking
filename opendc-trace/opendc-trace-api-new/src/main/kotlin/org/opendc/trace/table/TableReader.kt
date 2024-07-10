package org.opendc.trace.table

import org.opendc.trace.table.column.Column
import org.opendc.trace.table.column.ColumnReader
import org.opendc.trace.table.csv.CSVTableReader
import org.opendc.trace.util.errAndFalse
import org.opendc.trace.util.logger
import java.io.File
import java.nio.file.Path

public abstract class TableReader(
    artificialCols: Map<String, Any> = mutableMapOf()
) {

    private val artificialCols = artificialCols.toMutableMap()

    public abstract val tableName: String

    protected val colReaders: MutableMap<String, MutableList<ColumnReader<*, *>>> = mutableMapOf()

    public fun nextLine(): Boolean {
        return parseNextLine()
            .also {
                // if a new line has been parsed
                if (it)

                    // set artificial columns readers current values
                    // and call their respective post-processing functions
                    artificialCols.forEach { (colName, colValue) ->
                        colReaders[colName]?.forEach { cr -> cr.setArtificially(colValue) }
                    }
            }
    }

    protected abstract fun parseNextLine(): Boolean

    protected abstract val nonArtificialColNames: Set<String>

    internal val colNames: Set<String>
        get() = nonArtificialColNames + artificialCols.keys.toSet()

    public fun readAll() {
        executeUntilFalse { nextLine() }
    }

    public fun rmColumnReader(colReader: ColumnReader<*, *>) {
        colReaders[colReader.name]?.remove(colReader)
    }

    public open fun <T: Any> addColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<T>,
        postProcess: (T) -> Unit = {},
        defaultValue: T? = null,
        forceAdd: Boolean = false
    ): ColumnReader<T, T>? =
        addColumnReader(name = name, columnType = columnType, process = { it }, postProcess = postProcess, defaultValue = defaultValue, forceAdd = forceAdd)

    public open fun <O, P: Any> addColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<O>,
        process: (O) -> P,
        postProcess: (P) -> Unit = {},
        defaultValue: P? = null,
        forceAdd: Boolean = false
    ): ColumnReader<O, P>? {

        return if (colReaderAdditionPossible(colName = name, dfltValue = defaultValue, forceAdd = forceAdd)) {
            val newReader = ColumnReader(
                name = name,
                columnType = columnType,
                process = process,
                postProcess = postProcess
            )

            colReaders.getOrPut(name) { mutableListOf() }.add(newReader)

            newReader

        } else null
    }

    public fun <T: Any> withColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<T>,
        postProcess: (T) -> Unit,
        defaultValue: T? = null,
        forceAdd: Boolean = false
    ): TableReader? =
        withColumnReader(name = name, columnType = columnType, process = { it }, postProcess = postProcess, defaultValue = defaultValue, forceAdd = forceAdd)

    public fun <O, P: Any> withColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<O>,
        process: (O) -> P,
        postProcess: (P) -> Unit,
        defaultValue: P? = null,
        forceAdd: Boolean = false
    ): TableReader? {
        addColumnReader(
            name = name,
            columnType = columnType,
            process = process,
            defaultValue = defaultValue,
            postProcess = postProcess,
            forceAdd = forceAdd
        ) ?: return null

        return this
    }

    public fun <T: Any> addColumnReader(
        column: Column<T>,
        postProcess: (T) -> Unit = {},
        defaultValue: T? = null,
        forceAdd: Boolean = false
    ): ColumnReader<T, T>? {
        return this.addColumnReader(
            name = column.name,
            columnType = column.type,
            defaultValue = defaultValue,
            postProcess =postProcess,
            forceAdd = forceAdd
        )
    }

    public fun <O, P: Any> addColumnReader(
        column: Column<O>,
        process: (O) -> P,
        postProcess: (P) -> Unit = {},
        defaultValue: P? = null,
        forceAdd: Boolean = false
    ): ColumnReader<O, P>? {
        return this.addColumnReader(
            name = column.name,
            columnType = column.type,
            defaultValue = defaultValue,
            process = process,
            postProcess = postProcess,
            forceAdd = forceAdd
        )
    }

    public fun <T: Any> withColumnReader(
        column: Column<T>,
        postProcess: (T) -> Unit,
        defaultValue: T? = null,
        forceAdd: Boolean = false
    ): TableReader? {
        this.addColumnReader(
            name = column.name,
            columnType = column.type,
            defaultValue = defaultValue,
            postProcess = postProcess,
            forceAdd = forceAdd
        ) ?: return null

        return this
    }

    public fun <O, P: Any> withColumnReader(
        column: Column<O>,
        process: (O) -> P,
        postProcess: (P) -> Unit,
        defaultValue: P? = null,
        forceAdd: Boolean = false
    ): TableReader? {
        this.addColumnReader(
            name = column.name,
            columnType = column.type,
            defaultValue = defaultValue,
            process = process,
            postProcess = postProcess,
            forceAdd = forceAdd
        ) ?: return null

        return this
    }

    private fun colReaderAdditionPossible(colName: String, dfltValue: Any?, forceAdd: Boolean = false): Boolean {
        if (colName !in colNames) {
            if (forceAdd) {
                artificialCols[colName] = dfltValue
                    ?. also { log.warn("forcing column reader for column '$colName' which is not present in table '$tableName', " +
                        "it will always return the default value '$dfltValue'. It is expected behaviour in " +
                        "concatenated tables where the columns are not 1:1. Post processing functions will be called on the default value for each row.")
                    } ?: return log.errAndFalse("unable to force column reader addition in table '$tableName' for column name '$colName', no default value provided.")

            } else return log.errAndFalse("unable to add column reader for column $colName, " +
                "column does not exists in table '$tableName' and 'forceAdd' was not set")
        }

        return true
    }


    public companion object {
        private val log by logger()

        internal fun genericReaderFor(file: File, artificialColumns: Map<String, Any>, tableName: String): TableReader? =
            when (file.extension) {
                "csv" -> CSVTableReader(file, artificialColumns = artificialColumns, tableName = tableName)
                else -> null // TODO: add error log
            }

        public fun genericReaderFor(file: File): TableReader? =
            genericReaderFor(file = file, artificialColumns = emptyMap(), tableName = file.nameWithoutExtension)

        public fun genericReaderFor(path: Path): TableReader? =
            genericReaderFor(path.toFile())

        public fun genericReaderFor(path: String): TableReader? =
            genericReaderFor(File(path))
    }
}

private fun executeUntilFalse(f: () -> Boolean) {
    while (f.invoke()) { /* meow */ }
}
