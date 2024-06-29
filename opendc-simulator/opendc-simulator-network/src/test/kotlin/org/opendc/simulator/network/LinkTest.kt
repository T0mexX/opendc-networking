package org.opendc.simulator.network

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.flow.EndToEndFlow
import org.opendc.simulator.network.utils.Kbps

class LinkTest: FunSpec({
    context("congested link should give each flow bandwidth proportional to their desired data rate") {
        data class TestData(val flowRates: List<Kbps>, val bwPerc: Double)

        val testDataGenerator: Arb<TestData> = Arb.bind(
            Arb.int(2..20),
            Arb.double(.0..1.0)
        ) { numOfFlows, bwPerc ->
            val arbDouble = Arb.double(10.0..10000.0)
            val flowRates = buildList<Kbps> {
                repeat(numOfFlows) { add(arbDouble.next()) }
            }

            TestData(flowRates, bwPerc)
        }

        checkAll(iterations = 10, testDataGenerator) { testData ->
            context("sending flow rates ${testData.flowRates} with bandwidth equal to ${testData.bwPerc} of the sum of flows") {
                val eToEndFlows = testData.flowRates.mapIndexed { index, rate ->
                    EndToEndFlow(
                        flowId = index,
                        senderId = 0,
                        destId = 1,
                        totalDataToTransmit = Double.MAX_VALUE,
                        desiredDataRate = rate
                    )
                }

                val totDataRate: Kbps = eToEndFlows.sumOf { it.desiredDataRate }
                val sender = CoreSwitch(id = 0, portSpeed = Double.MAX_VALUE, numOfPorts = 1)
                val receiver = CoreSwitch(id = 1, portSpeed = totDataRate * testData.bwPerc, numOfPorts = 1) // only way currently to limit link bandwidth
                sender.connect(receiver)

                eToEndFlows.forEach {
                    receiver.addReceivingEtoEFlow(it)
                    sender.startFlow(it)
                }

                withData(eToEndFlows) {
                    it.currDataRate shouldBe ((it.desiredDataRate * testData.bwPerc) plusOrMinus 1.0)
                }
            }
        }
    }
})
