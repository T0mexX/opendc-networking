package org.opendc.trace

import kotlinx.coroutines.flow.merge
import org.opendc.trace.preset.BitBrains
import org.opendc.trace.table.column.ColumnReader
import org.opendc.trace.table.Table
//import org.opendc.trace.table.Table.Companion.concatWithName
import org.opendc.trace.table.TableReader
import org.opendc.trace.table.VirtualTable
import org.opendc.trace.table.concatWithName
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.collections.ArrayList


private fun main() {


//    val bitbrains: BitBrains = BitBrains.fromFolderWithVmTables(File("resources"))
//
//    val metTbl = bitbrains.metaTable
//
//    val rd = metTbl.concatWithName(bitbrains.allVmsTable, "merged").getReader()
//
//
//    val idRd = rd.addColumnReader(BitBrains.VM_ID, defaultValue = -1)!!
//    val tsRd = rd.addColumnReader(BitBrains.TIMESTAMP, defaultValue = -2, forceAdd = true)!!
//    val startRd = rd.addColumnReader(BitBrains.START_TIME, defaultValue = 0, forceAdd = true)!!
//
//    (0..10).forEach {
//        rd.nextLine()
//        println("id=${idRd.currRowValue}, timestamp=${tsRd.currRowValue}, startTime=${startRd.currRowValue}")
//    }
//    println("A")
//    val tblReader = metTbl.getReader()
//    println("B")

//    val vTable: VirtualTable = VirtualTable(
//        name = "meta",
//        sequence {
//            bitbrains.tablesByName.mapValues { (_, vmTable) -> vmTable.getReader() }
//                .forEach { (vmName, rd) ->
//                    val timestampRd = rd.addColumnReader(BitBrains.TIMESTAMP)!!
//
//                    rd.nextLine()
//                    val startTime: Long = timestampRd.currRowValue
//                    rd.readAll()
//                    val endTime: Long = timestampRd.currRowValue
//
//                    yield(mapOf(
//                        "id" to vmName,
//                        "start_time" to startTime.toString(),
//                        "end_time" to endTime.toString()
//                    ))
//                }
//        }
//    )
//
//    val vRd = vTable.getReader()
//    val idRd = vRd.addColumnReader(BitBrains.VM_ID)!!
//    val startRd = vRd.addColumnReader("start_time", ColumnReader.LongType)!!
//    val endRd = vRd.addColumnReader("end_time", ColumnReader.LongType)!!
//
//
//    (0..3).forEach {
//        vRd.nextLine()
//        println("id=${idRd.currRowValue}, start=${startRd.currRowValue}, end=${endRd.currRowValue}, elapsed=${Duration.ofMillis(endRd.currRowValue - startRd.currRowValue).toMinutes()}")
//    }
//
//
//
//
    val timeStampCol: String = "Timestamp [ms]"
    val netTxCol: String = "Network transmitted throughput [KB/s]"
////    val tableReader: TableReader = TableReader.genericReaderFor("resources/output.csv") !!
//
    val timeStamps = ArrayList<Long>(50000)
    val netTxs = ArrayList<Double>(50000)
    val ids = ArrayList<Int>(50000)
//
//    val vm1Table: Table = Table.withNameFromFile("1", File("resources/1.csv"))
//        ?.withArtificialColumn("id", 1) !!
//
//    val vm2Table: Table = Table.withNameFromFile("2", File("resources/2.csv"))
//        ?.withArtificialColumn("id", 2) !!
//
//
//    val vm3Table: Table = Table.withNameFromFile("3", File("resources/3.csv"))!!
////        ?.withArtificialColumn("id", 3) !!
//
//    val merged: Table = vm1Table.concatWithName(vm2Table, "bo").concatWithName(vm3Table, "bo")
////    val merged: Table = Table.concatWithName(
////        listOf(vm1Table, vm2Table, vm3Table),
////        "bo",
////    )
//
//    val tr: TableReader = merged.getReader()
//
//////
//////
//////
//    tr.withColumnReader(timeStampCol, ColumnReader.LongType, postProcess = { timeStamps.add(it) })
//        ?.withColumnReader(netTxCol, ColumnReader.DoubleType, process = { it * 8 }, postProcess = { netTxs.add(it) })
//        ?.withColumnReader(
//            name = "id",
//            ColumnReader.IntType,
//            defaultValue = -1,
//            process = { it * 8 }, // not applied to artificially added columns
//            postProcess = { ids.add(it) },
//            forceAdd = true
//        ) !!
////
////
//    tr.readAll()
//



    val bitBrains: BitBrains = BitBrains.fromFolderWithVmTables(File("resources"))

    val vm1table: Table = bitBrains.tablesByName["1"]!!
    val vm2 = bitBrains.tablesByName["2"]!!
    val vm3 = bitBrains.tablesByName["3"]!!
    val vm4 = bitBrains.tablesByName["4"]!!

    val merged = vm1table.concatWithName(vm2, "").concatWithName(vm3, "").concatWithName(vm4, "merged")

    val reader = merged.getReader()
//    val reader = bitBrains.allVmsTable.getReader()

    reader.withColumnReader(timeStampCol, ColumnReader.LongType, postProcess = { timeStamps.add(it) })
        ?.withColumnReader(netTxCol, ColumnReader.DoubleType, process = { it * 8 }, postProcess = { netTxs.add(it) })
        ?.withColumnReader(
            name = "id",
            ColumnReader.IntType,
            defaultValue = -1,
            process = { it * 8 }, // not applied to artificially added columns
            postProcess = { ids.add(it) },
            forceAdd = true
        ) !!

    reader.readAll()

    println(ids.size)
//    (48000..48010).forEach {
//        println()
//        print("id: ${ids[it]}  |  ")
//        print("timestamp: ${timeStamps[it]}  |  ")
//        print("netTxs: ${netTxs[it]}")
//        println()
//    }

}
