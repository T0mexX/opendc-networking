package org.opendc.trace.preset

import org.opendc.trace.Trace
import org.opendc.trace.table.column.ColumnReader
import org.opendc.trace.table.column.ColumnReader.ColumnType
import org.opendc.trace.table.Table
import org.opendc.trace.table.TableReader
import org.opendc.trace.table.column.Column
import org.opendc.trace.util.logger
import java.io.File

public class BitBrains private constructor(vmsTables: List<Table>) : Trace {

    public val allVmsTable: Table =
        Table.concatWithName(tables = vmsTables, name = "all_vms")

    override val tablesByName: Map<String, Table> =
        (vmsTables + allVmsTable).associateBy { it.name }

    // TODO: add "meta"


    public companion object {

        public val NET_TX: Column<Double> = object: Column<Double>() {
            override val type: ColumnType<Double> = ColumnReader.DoubleType
            override val name: String = "Network transmitted throughput [KB/s]"
        }
        public val NET_RX: Column<Double> = object: Column<Double>() {
            override val type: ColumnType<Double> = ColumnReader.DoubleType
            override val name: String = "Network received throughput [KB/s]"
        }
        public val VM_ID: Column<Int> = object: Column<Int>() {
            override val type: ColumnType<Int> = ColumnReader.IntType
            override val name: String = "id"
        }
        public val TIMESTAMP: Column<Long> = object: Column<Long>() {
            override val type: ColumnType<Long> = ColumnReader.LongType
            override val name: String = "Timestamp [ms]"
        }

        private val log by logger()


        public fun fromFolderWithVmTables(directory: File): BitBrains {
            require(directory.exists() && directory.isDirectory)
            { "unable to create BitBrains trace, directory does not exist" }

            val vmTables: List<Table> = directory.listFiles()
                ?.filter { it.isFile && it.nameWithoutExtension.toIntOrNull() != null }
                ?.map { file ->
                    Table.withNameFromFile(
                        name = file.nameWithoutExtension,
                        file = file
                    )?.withArtificialColumn(
                        columnName = "id",
                        value = file.nameWithoutExtension.toIntOrNull()
                            ?: let {
                                log.warn("unable to cast file name '${file.nameWithoutExtension} to vm id (Int), defaulting to -1")
                                -1
                            }
                    )
                        ?: log.warn("unable to instantiate vm table for file ${file.name}")
                }
                ?.filterIsInstance<Table>() // filters nulls
                ?: emptyList()

            vmTables.ifEmpty { throw IllegalArgumentException("no vm tables found in directory ${directory.absolutePath}") }

            return BitBrains(vmTables)
        }
    }
}
