package org.opendc.simulator.network.policies.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.units.DataRate
import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt

@Serializable
@SerialName("first_come_first_served")
internal data object FirstComeFirstServed: FairnessPolicy {
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
