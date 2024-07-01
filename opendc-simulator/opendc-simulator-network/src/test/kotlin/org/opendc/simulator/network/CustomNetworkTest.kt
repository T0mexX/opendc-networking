package org.opendc.simulator.network

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.CustomNetwork.CustomNetworkSpecs
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.components.Switch
import java.io.File

@OptIn(ExperimentalSerializationApi::class, ExperimentalKotest::class)
class CustomNetworkTest: FunSpec({
    val jsonReader = Json { ignoreUnknownKeys = true }

    context("Build network from json file") {

        File("src/test/resources/custom-network/specs-test1.json").let { testFile ->
            context("convert json file to network specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()) }
            }
        }

        File("src/test/resources/custom-network/specs-test2.json").let { testFile ->
            context("build network from specs").config(enabled = testFile.exists()) {
                shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).buildFromSpecs() }
            }
        }

        context("build network from incorrect specs") {
            File("src/test/resources/custom-network/specs-duplicate-node-id.json").let { testFile ->
                context("duplicate node ids (eliminating duplicates)").config(enabled = testFile.exists()) {
                    shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).buildFromSpecs() }
                }
            }

            File("src/test/resources/custom-network/specs-undeployable-link.json").let { testFile ->
                context("link between non-existing nodes (ignoring)").config(enabled = testFile.exists()) {
                    shouldNotThrowAny { jsonReader.decodeFromStream<Specs<Network>>(testFile.inputStream()).buildFromSpecs() }
                }
            }

            File("src/test/resources/custom-network/specs-bad-link-array-sizes.json").let { testFile ->
                context("link arrays sizes not equal 2").config(enabled = testFile.exists()) {
                    shouldNotThrowAny {
                        val customNet: CustomNetworkSpecs =
                            jsonReader.decodeFromStream<Specs<CustomNetwork>>(testFile.inputStream()) as CustomNetworkSpecs
                        customNet.links shouldContainExactlyInAnyOrder listOf(Pair(0, 3), Pair(0, 4))
                    }
                }
            }
        }
    }

    context("build correct links from link list") {
        data class TestData(val nodes: List<Node>, val linkList: List<Pair<NodeId, NodeId>>)
        fun Node.isConnectedTo(other: Node): Boolean = this.portToNode.contains(other.id)

        val testDataGenerator: Arb<TestData> = Arb.bind(
            Arb.int(2..20),
            Arb.int(1..10)
        ) { numOfNodes, numOfLinks ->
            val nodes: List<Node> = buildList {
                (0..<numOfNodes).forEach { id ->  add(Switch(id = id, numOfPorts = numOfNodes, portSpeed = .0)) }
            }
            val links: List<Pair<NodeId, NodeId>> = buildList {
                val arbNodeId = Arb.int(0..<numOfNodes)
                repeat(numOfLinks) { add(Pair(arbNodeId.next(), arbNodeId.next())) }
            }
            TestData(nodes, links)
        }

        checkAll(iterations = 100 ,testDataGenerator) { testData ->
            context("Test with nodes ${testData.nodes} and links ${testData.linkList}") {
                val nodes: List<Node> = testData.nodes
                val links: List<Pair<NodeId, NodeId>> = testData.linkList
                val network = CustomNetwork(nodes)
                network.connectFromLinkList(links)
                withData(links) { link ->
                    if (link.first != link.second) {
                        network.nodes[link.first]
                            ?.isConnectedTo(network.nodes[link.second]!!)
                            ?.shouldBeTrue()
                    } else {
                        network.nodes[link.first]
                            ?.isConnectedTo(network.nodes[link.second]!!)
                            ?.shouldBeFalse()
                    }
                }
            }
        }
    }
})
