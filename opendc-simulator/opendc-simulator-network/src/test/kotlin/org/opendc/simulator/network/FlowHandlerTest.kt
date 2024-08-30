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

package org.opendc.simulator.network

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.components.connect
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.utils.ifNull0

class FlowHandlerTest : FunSpec({

    /**
     * Each [OutFlow] stores its rates per port
     * ([OutFlow.outRatesByPort]) itself instead of fetching from the
     * link table for performance reasons. Each OutFlow table is a fraction
     * of the number of ports of the node, usually 1-4. The link table can have
     * hundreds ot thousands of entries. This redundancy needs to remain consistent.
     */
    context("OutFlow rateByPort and totRateOut consistency with Link rates") {
        data class TestData(val flowUpdts: List<Pair<FlowId, DataRate>>, val numOfFlows: Int, val numOfInBetweenSwitches: Int)

        val testDataGenerator: Arb<TestData> =
            Arb.bind(
                Arb.int(2..50),
                Arb.int(50..100),
                Arb.int(2..10),
            ) { numOfFlows, numOfUpdts, numOfInBetweenSwitches ->
                val arbFlowId = Arb.long(0L..<numOfFlows)
                val rateTracker: MutableMap<FlowId, DataRate> = (0L..<numOfFlows).associateWith { DataRate.ZERO }.toMutableMap()
                val updts =
                    buildList {
                        (1..numOfUpdts).forEach { _ ->
                            val fId = arbFlowId.next()
                            // Ensures rate >= 0.
                            val deltaRate =
                                arbitrary {
                                    DataRate.ofKbps(
                                        Arb.double(
                                            -rateTracker.getOrDefault(fId, DataRate.ZERO).toKbps()..1000.0,
                                        ).next(),
                                    )
                                }.next()
                            rateTracker.compute(fId) { _, oldRate -> (oldRate.ifNull0()) + deltaRate }
                            add(Pair(fId, deltaRate))
                        }
                    }

                TestData(updts, numOfFlows, numOfInBetweenSwitches)
            }

        checkAll(iterations = 5, testDataGenerator) { testData ->
            NetFlow.reset()
            val sender = CoreSwitch(id = -1, numOfPorts = testData.numOfInBetweenSwitches + 1, portSpeed = DataRate.ofKbps(10000.0))
            val receiver = CoreSwitch(id = -2, numOfPorts = testData.numOfInBetweenSwitches + 1, portSpeed = DataRate.ofKbps(10000.0))
            val inBetweenSwitches =
                buildList {
                    (0L..<testData.numOfInBetweenSwitches).forEach { id ->
                        add(Switch(id = id, numOfPorts = 2, portSpeed = DataRate.ofKbps(1000.0)))
                    }
                }

            repeat(testData.numOfFlows + 1) { NetFlow(transmitterId = sender.id, destinationId = receiver.id) }

            inBetweenSwitches.forEach {
                it.connect(sender)
                it.connect(receiver)
            }
            val fh: FlowHandler = sender.flowHandler
            val nFlows: Map<FlowId, OutFlow> = fh.outgoingFlows

//            // Adds the flows to the outgoing flows table by updates of .0 rate.
//            // The purpose of this test is not to test whether th update mechanism works.
//            with(fh) { sender.updtFlows(  (0..<testData.numOfFlows).associateWith { .0 }  ) }

            suspend fun OutFlow?.isConsistent(id: FlowId) {
                context("rate in the OutFlow table should match the rate on the Link") {
                    this@isConsistent?.let {
                        outRatesByPort.forEach { (port, rate) ->
                            rate.toKbps() shouldBe (port.outgoingRateOf(this@isConsistent.id).toKbps() plusOrMinus 0.00001)
                        }
                        totRateOut.toKbps() shouldBe (outRatesByPort.values.sumOf { it.toKbps() } plusOrMinus 0.00001)
                    } ?: let {
                        sender.ports.sumOf { it.outgoingRateOf(id).toKbps() } shouldBeExactly .0
                    }
                }

                context("none of the port rates for this flow should be negative (might be Link fault)") {
                    this@isConsistent?.outRatesByPort?.values?.forEach { it.toKbps() shouldBeGreaterThanOrEqual .0 }
                }

                context("total rate out should be equal to the sum of the rates for each port") {
                    this@isConsistent?.let {
                        totRateOut.toKbps() shouldBe (outRatesByPort.values.sumOf { it.toKbps() } plusOrMinus 0.00001)
                    }
                }
            }

            testData.flowUpdts.forEach { (fId, deltaRate) ->
                runBlocking {
                    with(fh) { sender.updtFlows(RateUpdt(fId to deltaRate)) }
                }
                nFlows[fId].isConsistent(fId)
            }
        }
    }
})
