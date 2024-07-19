package org.opendc.simulator.network.api

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.FatTreeNetwork
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.StabilityValidator
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.components.connect
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.policies.forwarding.StaticECMP
import org.opendc.trace.preset.BitBrains
import java.io.File
import java.time.Duration
import kotlin.system.measureNanoTime

private fun bitBrainsSim() {
    val bitBrains = BitBrains.fromFolderWithVmTables(File("resources/traces/bitbrains"))
    val wl: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..125)
//    val wl2: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..250)

    val net = FatTreeNetwork(allSwitchSpecs = Switch.SwitchSpecs(portSpeed = 1000.0, numOfPorts = 8))

    wl.execOn(net)
    println(Duration.ofNanos(StaticECMP.nano).seconds)
    println(Duration.ofNanos(NetworkController.nano).seconds)
//    wl2.execOn(net)


//    wl.execOn(File("resources/bo.json"))
}

private suspend fun bo() {
    val h1 = HostNode(
        id = 0,
        portSpeed = 1000.0,
        numOfPorts = 1,
    )
    val h2 = HostNode(
        id = 1,
        portSpeed = 1000.0,
        numOfPorts = 1,
    )
    h1.connect(h2)

    val net = CustomNetwork(listOf(h1, h2))

    StaticECMP.eToEFlows = net.netFlowById

    val stbl = StabilityValidator()

    val f = NetFlow(
        transmitterId = 0,
        destinationId = 1,
        id = 0,
        desiredDataRate = 1.0
    )




    coroutineScope {
        val netJob = net.launch()


        delay(500)

        println("\nTESTING START")

        val bo = measureNanoTime {
            net.startFlow(f)
            stbl.awaitStability()
        }
        println(bo)

        println(h1.getFmtFlows())
        println(h2.getFmtFlows())

        netJob.cancelAndJoin()
    }
}


internal fun main() {
    bitBrainsSim()
}


private interface Bo {
    val bo: Int
}

private interface Bo2 {
    val bo: Int
}

private class meow: Bo, Bo2 {
    override val bo = 1
}

