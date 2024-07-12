package org.opendc.simulator.network.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.api.simworkloads.NetworkEvent
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.FatTreeNetwork
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Switch
import org.opendc.trace.preset.BitBrains
import org.opendc.trace.table.Table
import org.opendc.trace.table.concatWithName
import java.io.File
import java.time.Duration
import kotlin.system.measureNanoTime

internal fun main() {
    startTime = System.currentTimeMillis()

    val bitbrainsTraceDir = File("resources/traces/bitbrains")
    val bitBrains = BitBrains.fromFolderWithVmTables(bitbrainsTraceDir)

    printlnWithTimeElapsed("trace created")

//    val tr: TableReader = bitBrains.allVmsTable.getReader()

    val tr = bitBrains.tablesByName.filterKeys { it != "meta" }.values.toList()
        .slice(0..124).let { Table.concatWithName(it, "BO") }
        .getReader()

    val txRd = tr.addColumnReader(BitBrains.NET_TX, process = { it * 8 /* KBps to Kbps */ } ) !!
    val rxRd = tr.addColumnReader(BitBrains.NET_RX, process = { it * 8 /* KBps to Kbps */ } ) !!
    val deadlineRd = tr.addColumnReader(BitBrains.TIMESTAMP) !!
    val idRd = tr.addColumnReader(BitBrains.VM_ID, process = { it.toLong() } ) !!


    printlnWithTimeElapsed("table reader set up")

    val hostIds = mutableSetOf<NodeId>()
    val events = buildList<NetworkEvent> {
        while (tr.nextLine()) {

            hostIds.add(idRd.currRowValue)

            // add network update event for flow to the internet
            add(NetworkEvent.FlowUpdate(
                deadline = deadlineRd.currRowValue,
                desiredDataRate = txRd.currRowValue,
                from = idRd.currRowValue,
                to = INTERNET_ID
            ))

            // add network update event for flow from the internet
            add(
                NetworkEvent.FlowUpdate(
                deadline = deadlineRd.currRowValue,
                desiredDataRate = rxRd.currRowValue,
                from = INTERNET_ID,
                to = idRd.currRowValue
            ))
        }
    }

    printlnWithTimeElapsed("event list computed")

    val wl = SimNetWorkload(netEvents = events, hostIds = hostIds)



    val jsonParser = Json { ignoreUnknownKeys = true }
    val ss: Switch.SwitchSpecs = Switch.SwitchSpecs(numOfPorts = 9, 1000.0)
    val fatTree: Network = FatTreeNetwork(ss, ss, ss)

//    bitBrains.tablesByName.values.toList().slice(0..124).let { Table.concatWithName(it, "BO") }

//    val controller: NetworkController = NetworkController.fromFile(File("resources/bo.json"))
    val controller = NetworkController(fatTree)

    val execTimes = mutableListOf<Long>()
//    repeat (10) {
        execTimes.add(measureNanoTime {
            controller.execWorkload(
                netWorkload = wl,
                resetTime = true,
                resetFlows = true,
                resetEnergy = true,
                resetVirtualMapping = true,
                resetClaimedNodes = true
            )
            println(controller.energyRecorder.getFmtReport())
        })
//    }

    println(execTimes)
    println(execTimes.sum() / execTimes.size)
    /* with prints
        [705,752,363, 973677, 183720, 367882, 517529, 298741, 290398, 419388, 249994, 498478]
        70,955,217
     */
    /**
     *
        [124,899,430, 442121, 49696, 40559, 60837, 38463, 34663, 30513, 29611, 35600]
        12,566,149
     */
    /*
        [135,389,977, 469759, 61663, 49326, 40827, 38904, 33668, 34225, 58182, 42717]
        13,621,924
     */

    printlnWithTimeElapsed("workload run")
}


private var startTime: Long = Long.MIN_VALUE

internal fun printlnWithTimeElapsed(printable: Any?) {
    val elapsedTime = System.currentTimeMillis() - startTime
    println("$elapsedTime ms : $printable")
}
