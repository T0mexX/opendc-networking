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
    val wl: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..80)
//    val opt = wl.optimize()
//    val wl2: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..250)

    val net = FatTreeNetwork(allSwitchSpecs = Switch.SwitchSpecs(portSpeed = 1.0, numOfPorts = 8))
//    println(net.allNodesToString())

    wl.execOn(net)
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

