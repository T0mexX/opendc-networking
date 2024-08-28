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

package org.opendc.trace.preset

import org.opendc.trace.Trace
import org.opendc.trace.table.Table
import org.opendc.trace.table.VirtualTable
import org.opendc.trace.table.column.Column
import org.opendc.trace.table.column.ColumnReader
import org.opendc.trace.table.column.ColumnReader.ColumnType
import org.opendc.trace.table.concatWithName
import org.opendc.trace.util.logger
import java.io.File

public class BitBrains private constructor(vmsTables: List<Table>) : Trace {
    public val allVmsTable: Table =
        Table.concatWithName(tables = vmsTables, name = "all_vms")

    public val metaTable: Table

    override val tablesByName: Map<String, Table>

    init {
        metaTable =
            VirtualTable(
                name = "meta",
                rowSeq =
                    sequence {
                        vmsTables.map { vmTable -> vmTable.getReader() }
                            .forEach { rd ->
                                val tableName: String = rd.tableName
                                val timestampRd = rd.addColumnReader(BitBrains.TIMESTAMP)!!

                                rd.nextLine()
                                val startTime: Long = timestampRd.currRowValue
                                rd.readAll()
                                val endTime: Long = timestampRd.currRowValue

                                yield(
                                    mapOf(
                                        "id" to tableName,
                                        "start_time" to startTime.toString(),
                                        "end_time" to endTime.toString(),
                                    ),
                                )
                            }
                    },
            )

        tablesByName = (vmsTables + allVmsTable + metaTable).associateBy { it.name }
    }

    public fun vmTablesInRange(range: IntRange = 1..1250): List<Table> =
        buildList {
            range.forEach {
                add(
                    tablesByName.getOrElse(it.toString()) {
                        log.warn("vm table $it not found")
                        null
                    },
                )
            }
        }.filterNotNull()

    public companion object {
        public val NET_TX: Column<Double> =
            object : Column<Double>() {
                override val type: ColumnType<Double> = ColumnReader.DoubleType
                override val name: String = "Network transmitted throughput [KB/s]"
            }
        public val NET_RX: Column<Double> =
            object : Column<Double>() {
                override val type: ColumnType<Double> = ColumnReader.DoubleType
                override val name: String = "Network received throughput [KB/s]"
            }
        public val VM_ID: Column<Int> =
            object : Column<Int>() {
                override val type: ColumnType<Int> = ColumnReader.IntType
                override val name: String = "id"
            }
        public val TIMESTAMP: Column<Long> =
            object : Column<Long>() {
                override val type: ColumnType<Long> = ColumnReader.LongType
                override val name: String = "Timestamp [ms]"
            }
        public val START_TIME: Column<Long> =
            object : Column<Long>() {
                override val type: ColumnType<Long> = ColumnReader.LongType
                override val name: String = "start_time"
            }
        public val END_TIME: Column<Long> =
            object : Column<Long>() {
                override val type: ColumnType<Long> = ColumnReader.LongType
                override val name: String = "end_time"
            }

        private val log by logger()

        public fun fromFolderWithVmTables(directory: File): BitBrains {
            require(directory.exists() && directory.isDirectory) { "unable to create BitBrains trace, directory does not exist" }

            val vmTables: List<Table> =
                directory.listFiles()
                    ?.filter { it.isFile && it.nameWithoutExtension.toIntOrNull() != null }
                    ?.map { file ->
                        Table.withNameFromFile(
                            name = file.nameWithoutExtension,
                            file = file,
                        )?.withArtificialColumn(
                            columnName = "id",
                            value =
                                file.nameWithoutExtension.toIntOrNull()
                                    ?: let {
                                        log.warn("unable to cast file name '${file.nameWithoutExtension} to vm id (Int), defaulting to -1")
                                        -1
                                    },
                        )
                            ?: log.warn("unable to instantiate vm table for file ${file.name}")
                    }
                    ?.filterIsInstance<Table>() // filters nulls
                    ?: emptyList()

            vmTables.ifEmpty { throw IllegalArgumentException("no vm tables found in directory ${directory.absolutePath}") }

            if (vmTables.size != 1250) {
                log.warn("original bitbrains trace has 1250 vms but only ${vmTables.size} where found in folder ${directory.absolutePath}")
            }

            return BitBrains(vmTables)
        }
    }
}
