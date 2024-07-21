package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.utils.logger

//internal class FirstComeFirstServed: FairnessPolicy {
//
//    private val log by logger()
//
////    var n: Double = .0
////    var counter: Long = 0
//    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
//        updt.toList().sortedBy { (_, deltaRate) -> deltaRate }
//            .forEach { (fId, _) ->
//                outgoingFlows[fId]?.tryUpdtRate()
//            }
//
////        n = (n * counter + unsatisfiedFlowsSortedByRate.size) / ++counter
////        check(outgoingFlows.values.none { it.demand.approxLarger(it.totRateOut) && (it !in unsatisfiedFlowsSortedByRate) })
////        unsatisfiedFlows.toList().forEach { it.tryUpdtRate() }
//
//    }
//}
