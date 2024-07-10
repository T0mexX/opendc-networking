package org.opendc.trace.table

import org.opendc.trace.table.column.ColumnReader
import org.opendc.trace.util.errAndNull
import org.opendc.trace.util.logger
import java.io.File
import java.nio.file.Path

public abstract class Table {

    /**
     * The artificially added columns.
     * @see[withArtificialColumn]
     */
    protected val artificialCols: MutableMap<String, Any> = mutableMapOf()

    /**
     * The name of the table.
     */
    public abstract val name: String

    /**
     * The size of the table in bytes.
     * If the table is a concatenation of multiple table, then it returns the sum of the sub-tables.
     */
    public abstract val sizeInBytes: Long

    /**
     * @return      A reader for the table, which enables stream-fashion reading, as well as automated.
     * @see[TableReader]
     */
    public abstract  fun getReader(): TableReader


    /**
     * Adds a (abstract) column to the table with each row of the column having value [value].
     * Useful to merge multiple tables whose origin has to be distinguished (an artificial id).
     * @param[columnName]   the new column name.
     * @param[value]        the value of each row of the new column.
     *
     * @return              the table with the new column if the addition was successful, null otherwise.
     *                      The addition can fail in case a column with the same name is already present.
     */
    public fun <T: Any> withArtificialColumn(columnName: String, value: T): Table? {
        if (columnName in getReader().colNames)
            return log.errAndNull("unable to add abstract column '$columnName', a column with the same name is already present")

        artificialCols[columnName] = value

        return this
    }





    public companion object {
        private val log by logger()

        /**
         * See overloaded method below.
         */
        public fun withNameFromFile(name: String, path: Path): PhysicalTable? =
            withNameFromFile(name = name, file = path.toFile())

        /**
         * See overloaded method below.
         */
        public fun withNameFromFile(name: String, path: String): PhysicalTable? =
            withNameFromFile(name = name, file = File(path))

        /**
         * @param[name]     name of the resulting table.
         * @param[file]     file with the table.
         * @return  a physical table (table taken directly from file) if [file] is a supported format, null otherwise.
         */
        public fun withNameFromFile(name: String, file: File): PhysicalTable? =
            // check if a table reader can be instantiated for the file
            TableReader.genericReaderFor(file)?.let {
                PhysicalTable(name = name, file = file)
            }

        /**
         * See overloaded function below.
         */
        public fun Table.concatWithName(other: Table, name: String, assert1to1: Boolean = false): Table =
            concatWithName(listOf(this, other), name, assert1to1)

        /**
         * Concatenates multiple tables.
         * Concatenated table can be concatenated themselves (to avoid possible
         * performance issues is preferable to concatenate all tables in a single call).
         * @param[tables]       the tables to be concatenated.
         * @param[name]         the name of the new table.
         * @param[assert1to1]   if `true` it asserts all tables have the same columns.
         * If tables are allowed to have different columns, when the entry is not found,
         * the [ColumnReader] will fall back to the default value.
         * @see[TableReader.addColumnReader]
         *
         * @return      the resulting concatenated [Table] which acts as if only one.
         */
        public fun concatWithName(tables: List<Table>, name: String, assert1to1: Boolean = false): Table {
            require(tables.isNotEmpty())
            if (tables.size == 1) return tables[0]

            // requires all table have the same column names (1:1).
            if (assert1to1) {
                val firstTableColNames: Set<String> = tables[0].getReader().colNames
                require (tables.all { it.getReader().colNames == firstTableColNames })
                { "unable to merge tables ${tables.fold("") { acc, table -> "$acc${table.name}, " } }column names differ and 'assert1to1' was set" }
            }

            return object : Table() {

                init {
                    // sets the added (abstract) columns of ***this*** as the set of the sub-tables added columns.
                    tables.forEach { this.artificialCols.putAll(it.artificialCols) }
                }

                override val name: String = name

                /**
                 * The sum of the sub-tables sizes (it works recursively if multiple concat calls have been executed).
                 */
                override val sizeInBytes: Long =
                    tables.sumOf { it.sizeInBytes }

                /**
                 * Acts as [TableReader] for a concatenated table.
                 */
                override fun getReader(): TableReader =
                    object : TableReader(artificialCols = artificialCols) {

                        override val tableName: String = name

                        private val readers: List<TableReader> =
                            tables.map { it.getReader() }

                        private var readerIdx: Int = 0

                        override val nonArtificialColNames: Set<String> =
                            readers.flatMap { it.colNames }.toSet()

                        /**
                         * Acts as [TableReader.addColumnReader] for a concatenated table.
                         * @param[defaultValue]     this param is useful, in addition to parsing errors,
                         * if the tables have different columns, hence some entries will not be present.
                         * The table can be forced to be 1:1 with [assert1to1].
                         * @see[TableReader.addColumnReader]
                         * @return  instance of [ColumnReader] that acts as if the table was 1.
                         */
                        override fun <O, P: Any> addColumnReader(
                            name: String,
                            columnType: ColumnReader.ColumnType<O>,
                            process: (O) -> P,
                            postProcess: (P) -> Unit,
                            defaultValue: P?,
                            forceAdd: Boolean
                        ): ColumnReader<O, P>? {
                            return if (name in colNames) {

                                try {
                                    // anonymous ColumnReader that assign a column reader to each of the sub tables
                                    // and acts as a single column reader for the concatenated table.
                                    // If the columns names are not 1:1 (the tables have different columns),
                                    // when the column is not found the reader fall backs to the default value
                                    val newColReader = object : ColumnReader<O, P>(name = name, process = process, columnType = columnType) {

                                        private val colReaders: List<ColumnReader<O, P>>

                                        // adds a column reader for each sub-table. If any addition fails,
                                        // all column reader added until then are removed and an exception is thrown.
                                        init {
                                            val colReadersByTr: Map<TableReader, ColumnReader<O, P>?> =
                                                readers.associateWith {
                                                    it.addColumnReader(
                                                        name = name,
                                                        columnType = columnType,
                                                        defaultValue = defaultValue,
                                                        process = process,
                                                        postProcess = postProcess,
                                                        forceAdd = forceAdd
                                                    )
                                                }


                                            // if any column reader addition (to the sub-tables) failed
                                            if (colReadersByTr.values.any { it == null }) {

                                                // remove all successfully added column readers
                                                colReadersByTr.forEach { (tr, cr) ->
                                                    if (cr != null) tr.rmColumnReader(cr)
                                                }

                                                throw RuntimeException("unable to instantiate concatenated table reader")
                                            }

                                            colReaders = colReadersByTr.values.filterIsInstance<ColumnReader<O, P>>()
                                            check(colReaders.size == colReadersByTr.size) // should never fail
                                        }

                                        override var currRowValue: P
                                            get() = colReaders.getOrNull(readerIdx)
                                                ?.currRowValue
                                                ?: throw RuntimeException("number of column readers for column $name  (${colReaders.size})" +
                                                    "in concatenated table does not match the number of tables ${readers.size}")
                                            set(_) { /* no setter */ }
                                    }

                                    colReaders.getOrPut(name) { mutableListOf() }
                                        .add(newColReader)

                                    return newColReader

                                    // unable to instantiate column reader
                                } catch (_: Exception) {

                                    return log.errAndNull("unable to add column reader. This is a concatenated table, " +
                                        "if sub-table columns are not 1:1 (same exact columns), then you may try " +
                                        "to set 'forceAdd' to true. The resulting table reader will return the " +
                                        "default value whenever the current sub-table does not have the column."
                                    )
                                }
                            } else null
                        }

                        /**
                         * Acts as [TableReader.parseNextLine] for a concatenated table.
                         * Parses a line of a sub-table.
                         * If sub-table end reached it passes to the next.
                         * @see[TableReader.parseNextLine]
                         * @return  `true` if a line has been parsed, `false` if all sub-tables have been parsed.
                         */
                        override fun parseNextLine(): Boolean {
                            return readers.getOrNull(readerIdx)
                                ?.nextLine()
                                ?.let {
                                    if (!it) {
                                        readerIdx++
                                        nextLine()
                                    } else true
                                } ?: false
                        }
                    }
            }
        }
    }
}
