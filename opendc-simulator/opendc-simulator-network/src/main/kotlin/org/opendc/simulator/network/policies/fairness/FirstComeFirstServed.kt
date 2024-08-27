package org.opendc.simulator.network.policies.fairness

import org.opendc.common.logger.logger
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt

internal object FirstComeFirstServed: FairnessPolicy {
    private val LOG by logger()

    override fun FlowHandler.applyPolicy(updt: RateUpdt) {
        execRateReductions(updt)

        updt.filter {
            it.value approxLarger DataRate.ZERO
        }.keys.forEach { flowId ->
            outgoingFlows[flowId]
                ?.tryUpdtRate()
        }
    }
}
