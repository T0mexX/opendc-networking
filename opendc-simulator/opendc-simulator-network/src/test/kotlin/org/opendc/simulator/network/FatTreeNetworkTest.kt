package org.opendc.simulator.network

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.FatTreeNetwork
import org.opendc.simulator.network.components.FatTreeNetwork.FatTreeTopologySpecs
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.Switch.SwitchSpecs
import org.opendc.simulator.network.units.DataRate
import java.io.File
import kotlin.math.pow

@OptIn(ExperimentalSerializationApi::class, ExperimentalKotest::class)
class FatTreeNetworkTest: FunSpec({
    val jsonReader = Json { ignoreUnknownKeys = true }

    context("convert json file to network specs") {
        File("src/test/resources/fat-tree-network/specs-k4.json").let { testFile ->
            context("convert json with generic switch specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()) }
            }
        }

        File("src/test/resources/fat-tree-network/specs-k4-override-layer.json").let { testFile ->
            context("convert json with per layer switch specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).build() }
            }
        }
    }

    context("build fat-tree from specs") {
        withData(
            SwitchSpecs(numOfPorts = 4, portSpeed = DataRate.ZERO),
            SwitchSpecs(numOfPorts = 6, portSpeed = DataRate.ZERO),
            SwitchSpecs(numOfPorts = 8, portSpeed = DataRate.ZERO),
//            SwitchSpecs(numOfPorts = 10, portSpeed = .0)
        ) { switchSpecs ->
            val k: Int = switchSpecs.numOfPorts
            val fatTree: FatTreeNetwork = FatTreeTopologySpecs(switchSpecs = switchSpecs).build()
            fatTree.pods.size shouldBeExactly k
            fatTree.leafs.size shouldBeExactly (k / 2).toDouble().pow(2).toInt() * k
            fatTree.torSwitches.size shouldBeExactly k * k / 2
            fatTree.aggregationSwitches.size shouldBeExactly k * k / 2
            fatTree.coreSwitches.size shouldBeExactly k * k / 4
            fatTree.endPointNodes.size shouldBeExactly (k * k * k) / 4 + k * k / 4 + 1 // INTERNET_ID
        }
    }
})
