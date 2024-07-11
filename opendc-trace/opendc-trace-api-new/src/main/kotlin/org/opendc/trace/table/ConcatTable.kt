package org.opendc.trace.table

import org.opendc.trace.table.column.ColumnReader
import org.opendc.trace.util.errAndNull
import org.opendc.trace.util.logger

/**
 * Concatenates [this] table to [other] table calling [Table.Companion.concatWithName].
 * @see[Table.Companion.concatWithName].
 */
public fun Table.concatWithName(other: Table, name: String, assert1to1: Boolean = false): Table =
    Table.concatWithName(listOf(this, other), name, assert1to1)

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
public fun Table.Companion.concatWithName(tables: List<Table>, name: String, assert1to1: Boolean = false): Table {
    require(tables.isNotEmpty())
    if (tables.size == 1) return tables[0]

    if (assert1to1) {
        val firstTableColNames: Set<String> = tables[0].getReader().colNames
        require (tables.all { it.getReader().colNames == firstTableColNames })
        { "unable to merge tables ${tables.fold("") { acc, table -> "$acc${table.name}, " } }" +
            "column names differ and 'assert1to1' was set" }
    }

    return ConcatTable(name = name, subTables = tables)
}




private class ConcatTable(
    override val name: String,
    private val subTables: List<Table>
): Table() {

    private companion object { val log by logger() }

    /**
     * The sum of the sub-tables sizes (it works recursively if multiple concat calls have been executed).
     */
    override val sizeInBytes: Long =
        subTables.sumOf { it.sizeInBytes }

    override fun getReader(artificialColumns: Map<String, Any>): TableReader =
        ConcatTblReader(artificialColumns)


    inner class ConcatTblReader(
        artificialColumns: Map<String, Any>
    ): TableReader(artificialColumns) {

        override val tableName: String = this@ConcatTable.name

        private var readerIdx: Int = 0

        private val readers: List<TableReader> =
            this@ConcatTable.subTables.map { it.getReader() }

        override val nonArtificialColNames: Set<String> =
            readers.flatMap { it.colNames }.toSet()

        /**
         * Acts as [TableReader.addColumnReader] for a concatenated table.
         * @param[defaultValue]     this param is useful, in addition to parsing errors,
         * if the tables have different columns, hence some entries will not be present.
         * The table can be forced to be 1:1 with 'assert1to1' on concat calls.
         * @see[TableReader.addColumnReader]
         * @see[Table.concatWithName]
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
            fun getRd(): ColumnReader<O, P> =
                ConcatColumnReader(
                    name = name,
                    columnType = columnType,
                    process = process,
                    postProcess = postProcess,
                    defaultValue = defaultValue,
                    forceAdd = forceAdd
                )

            return try {
                if (name in this@ConcatTblReader.colNames)
                    getRd()
                else if (forceAdd) {
                    defaultValue ?: return log.errAndNull("unable to force add column reader for column " +
                        "with name '$name' to concat table '$tableName', default value not provided")
                    withArtColumn(colName = name, dfltValue = defaultValue)
                    getRd()
                } else null
            } catch (e: Exception) {
                log.error(e.message)
                return log.errAndNull("unable to add column reader. This is a concatenated table, " +
                    "if sub-table columns are not 1:1 (same exact columns), then you may try " +
                    "to set 'forceAdd' to true. The resulting table reader will return the " +
                    "default value whenever the current sub-table does not have the column."
                )
            }
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


        inner class ConcatColumnReader<O, P: Any>(
            name: String,
            columnType: ColumnType<O>,
            process: (O) -> P,
            postProcess: (P) -> Unit,
            defaultValue: P?,
            forceAdd: Boolean
        ): ColumnReader<O, P>(name = name, columnType = columnType, process = process) {

            val subTablesColReaders: List<ColumnReader<O, P>>

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
                            ?: log.errAndNull("unable to add column reader for column '$name' to sub-table '${it.tableName}' in concatenated table '$tableName'")
                    }

                // if any column reader addition (to the sub-tables) failed
                if (colReadersByTr.values.any { it == null }) {

                    // remove all successfully added column readers
                    colReadersByTr.forEach { (tr, cr) ->
                        if (cr != null) tr.rmColumnReader(cr)
                    }

                    throw RuntimeException("unable to instantiate concatenated table reader")
                }


                subTablesColReaders = colReadersByTr.values.filterIsInstance<ColumnReader<O, P>>()
                check(subTablesColReaders.size == colReadersByTr.size) // should never fail
            }

            override var currRowValue: P
                get() = subTablesColReaders.getOrNull(this@ConcatTblReader.readerIdx)
                    ?.currRowValue
                    ?: throw RuntimeException("number of column readers for column $name  (${colReaders.size})" +
                        "in concatenated table does not match the number of tables ${readers.size}")
                set(_) { /* no setter */ }
        }
    }


}
