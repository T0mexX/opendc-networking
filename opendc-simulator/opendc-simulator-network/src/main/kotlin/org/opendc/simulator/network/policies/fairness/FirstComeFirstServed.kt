package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.components.internalstructs.FlowHandler
import org.opendc.simulator.network.components.internalstructs.RateUpdate
import org.opendc.simulator.network.utils.logger

internal object FirstComeFirstServed: FairnessPolicy {

    private val log by logger()

    override fun FlowHandler.applyPolicy(updt: RateUpdate) {
        updt.toList().sortedBy { (_, deltaRate) -> deltaRate }
            .forEach { (fId, _) ->
                outgoingFlows[fId]?.tryUpdtRate()
            }
    }
}
