package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.OutFlow
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.tracker.AllByDemand
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.approxLarger
import org.opendc.simulator.network.utils.approxLargerOrEqual
import org.opendc.simulator.network.utils.isSorted
import kotlin.math.min
import kotlin.system.exitProcess

internal object MaxMinPerPort: FairnessPolicy {
    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        resetAll()

        if (FairnessPolicy.VERIFY)
            check(ports.all { it.availableTxBW() approx it.maxPortToPortBW }) {
                println(ports.filter { it.isConnected }.map { it.availableTxBW() })
            }

        // All flows sorted by demand.
        val flows: List<OutFlow> = flowTracker[AllByDemand]
        if (FairnessPolicy.VERIFY) check(flows.isSorted { a, b -> a.demand.compareTo(b.demand) })

        // Each port of the node associated with the number of flows
        // that need to traverse it.
        val remainingFlowsPerPort: MutableMap<Port, Int> = buildMap {
            flows.forEach { flow ->
                flow.outRatesByPort.keys.forEach { port ->
                    compute(port) { _, oldValue ->
                        (oldValue ?: 0) + 1
                    }
                }
            }
        }.toMutableMap()


        // Max-min for each port multiple ports.
        flows.forEachIndexed { idx, outFlow ->
            val targetPorts: Collection<Port> = outFlow.outRatesByPort.keys
            val flowDmPerPort: Kbps = outFlow.demand / targetPorts.size

            targetPorts.forEach { port ->
                val remFlowsForThisPort: Int = remainingFlowsPerPort[port]!!
                val availableShare: Kbps = port.availableTxBW() / remFlowsForThisPort
                val targetRate: Kbps = min(availableShare, flowDmPerPort)

                val newRate: Kbps = outFlow.tryUpdtPortRate(port, targetRate)
                check(newRate approx targetRate) { "MaxMin policy error" }

                remainingFlowsPerPort[port] = remFlowsForThisPort - 1
            }
            flows.forEach { try { it.verify() } catch (e: Exception) { println("IDX($idx) (prevF: ${flows.slice(0..idx)}) ${e.message}") } }

        }

        if (FairnessPolicy.VERIFY) {
            check(remainingFlowsPerPort.values.all { it == 0 })
            verify()
        }
    }
}

private fun Port.availableTxBW(): Kbps =
    sendLink?.availableBW ?: .0


private fun FlowHandler.resetAll() {
    outgoingFlows.values.forEach {
        check(it.tryUpdtRate(.0) == .0) {"${it.id}"}
    }
}

private fun FlowHandler.verify() {
    fun OutFlow.passesThrough(port: Port): Boolean = port in outRatesByPort.keys

    val flows: List<OutFlow> = flowTracker[AllByDemand]

    flows.forEach { it.verify() }

    ports.forEach { p ->
        var prevOut: Kbps = .0

        flows.forEach { f ->
            if (f.passesThrough(p)) {
                val currOut: Kbps = p.outgoingRateOf(f.id)

                check(currOut approxLargerOrEqual  prevOut)
                { "MaxMin policy error "}

                prevOut = currOut
            }
        }
    }
}
