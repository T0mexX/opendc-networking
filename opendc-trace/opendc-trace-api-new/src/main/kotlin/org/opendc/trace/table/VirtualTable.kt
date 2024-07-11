package org.opendc.trace.table

import org.opendc.trace.util.logger

/**
 * Table created artificially, represented by a [Sequence] of rows.
 * The table is neither computed when it is created, nor when its [TableReader]
 * is retrieved (except the first row). It is only computed line by line while parsing it.
 *
 * This table can be concatenated (without it needing to be computed) with other tables as all tables can, through [Table.concatWithName].
 * It can be constructed either by providing a sequence of rows [rowSeq] or a map where each column name
 * is associated with a sequence of its values (through secondary constructor).
 *
 * As all tables, it can be read with its [TableReader], which presents
 * the same interface of all other tables, retrieved with [getReader].
 *
 * @see[TableReader]
 * @see[Companion.invoke]
 * @property[name]          the name of the table.
 * @property[rowSeq]        [Sequence] of rows, represented as a map from column
 * name [String] to row value for that column [String]
 */
public class VirtualTable(
    override val name: String,
    private val rowSeq: Sequence<Map<String, String>>
): Table() {
    public companion object {
        private val log by logger()

        /**
         * secondary constructor.
         * @param[colsSeqs]     a map from column name ([String]) to its values [Sequence] of [String]s.
         * @param[name]               name of the table
         */
        public operator fun invoke(
            name: String,
            colsSeqs: Map<String, Sequence<String>>
        ): VirtualTable {

            // column sequences converted to a sequence of rows, as the primary constructor requires.
            val rowSeq: Sequence<Map<String, String>> = sequence {
                val colsIterByCol = colsSeqs.mapValues { (_, seq) -> seq.iterator() }

                while (colsIterByCol.values.all { it.hasNext() }) {
                    val row: Map<String, String> = colsIterByCol.map { (colName, colIter) ->
                        colName to colIter.next()
                    }.toMap()

                    yield(row)
                }

                // if the column sequences provided have different sizes.
                // there is no way to know in advance, only logging
                // warning when the end of the table is reached while parsing.
                if (colsIterByCol.values.any { it.hasNext() }) {
                    log.warn("virtual table has columns with different sizes, " +
                        "interrupting read at the size of shortest column")
                }
            }

            return VirtualTable(name = name, rowSeq = rowSeq)
        }
    }

    /**
     * A virtual table is not written to disk. Its size in bytes is pointless / hard to determine.
     *
     * Check ***super*** docs [Table.sizeInBytes]
     */
    override val sizeInBytes: Long = 0


    override fun getReader(artificialColumns: Map<String, Any>): TableReader {
        return object: TableReader(artificialCols = artificialColumns) {

            /**
             * The first row is computed in order to retrieve the column names.
             * Hence, it is the only row computed more than once.
             *
             * Check ***super*** docs [TableReader.nonArtificialColNames]
             */
            override val nonArtificialColNames: Set<String> = rowSeq.first().keys

            override val tableName: String = this@VirtualTable.name

            /**
             * The use of [Sequence.iterator] makes sure each row is computed only once.
             * the use of [Sequence.elementAt] would recompute the entire sequence up until the index requested.
             *
             * Tables are usually parsed in stream-fashion, hence there is usually no need to read a previous line.
             * To read the table again just retrieve another [TableReader] instance.
             */
            val rowIterator: Iterator<Map<String, String>> = rowSeq.iterator()

            override fun parseNextLine(): Boolean {
                return if (rowIterator.hasNext()) {
                        val row: Map<String, String> = rowIterator.next()
                        row.forEach { (colName, strValue) ->
                            colReaders[colName]?.forEach {
                                it.setFromString(strValue)
                            }
                        }
                        true

                    } else false
            }
        }
    }
}
