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

package org.opendc.simulator.network.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.api.workload.SimNetWorkload
import org.opendc.simulator.network.components.FatTreeNetwork
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.export.network.DfltNetworkExportColumns
import org.opendc.simulator.network.export.node.DfltNodeExportColumns
import org.opendc.trace.preset.BitBrains
import java.io.File

internal fun main() {
    idk()
}

@OptIn(ExperimentalSerializationApi::class)
private fun idk() {
    val bitBrains = BitBrains.fromFolderWithVmTables(File("resources/traces/bitbrains"))
    val wl: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..50)
//    val wl = readNetworkWl(File("resources/test.parquet"))!!
//    val opt = wl.optimize()
//    val wl2: SimNetWorkload = SimNetWorkload.fromBitBrains(bitBrains, vmsRange = 1..250)

//    val net =
//        FatTreeNetwork(
//            allSwitchSpecs = Switch.SwitchSpecs(portSpeed = DataRate.ofMbps(1.0), numOfPorts = 4),
//            hostNodeSpecs = HostNode.HostNodeSpecs(portSpeed = DataRate.ofGbps(1.0), numOfPorts = 1),
//        )

    val spec =
        FatTreeNetwork.FatTreeTopologySpecs(
            switchSpecs = Switch.SwitchSpecs(portSpeed = DataRate.ofMbps(1.0), numOfPorts = 8),
            hostNodeSpecs = HostNode.HostNodeSpecs(portSpeed = DataRate.ofGbps(1.0), numOfPorts = 1),
        )
//    println(net.allNodesToString())

    val iStream = File("resources/scenario-test/net-scenario.json").inputStream()

    val scenario: NetworkScenario = Json.decodeFromStream(iStream)
    DfltNodeExportColumns
    DfltNetworkExportColumns
//    val scenario =
//        NetworkScenario(
//            networkSpecs = spec,
//            wl = wl,
//            exportConfig =
//                NetworkExportConfig(
//                    outputFolder = File("resources/bo"),
//                    networkExportColumns = Exportable.getAllLoadedColumns(),
//                    nodeExportColumn = Exportable.getAllLoadedColumns(),
//                    exportInterval = Time.ofMin(5),
//                ),
//        )

    scenario.run()
//
//
//    wl.execOn(net)
}
