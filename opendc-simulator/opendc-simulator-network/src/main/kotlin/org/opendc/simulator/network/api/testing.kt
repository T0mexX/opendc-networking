package org.opendc.simulator.network.api

import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.FatTreeNetwork
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.units.DataRate
import org.opendc.trace.preset.BitBrains
import java.io.File

private fun bitBrainsSim() {
    val bitBrains = BitBrains.fromFolderWithVmTables(File("resources/traces/bitbrains"))
    val wl: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..10)
//    val opt = wl.optimize()
//    val wl2: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..250)

    val net = FatTreeNetwork(
        allSwitchSpecs = Switch.SwitchSpecs(portSpeed = DataRate.ofMbps(1.0), numOfPorts = 4),
        hostNodeSpecs = HostNode.HostNodeSpecs(portSpeed = DataRate.ofGbps(1.0), numOfPorts = 1)
    )
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

