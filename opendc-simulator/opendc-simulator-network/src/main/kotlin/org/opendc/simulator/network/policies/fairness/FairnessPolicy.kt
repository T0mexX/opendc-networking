package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.components.internalstructs.FlowHandler
import org.opendc.simulator.network.components.internalstructs.RateUpdate

internal interface FairnessPolicy {
    fun FlowHandler.applyPolicy(updt: RateUpdate)
}
