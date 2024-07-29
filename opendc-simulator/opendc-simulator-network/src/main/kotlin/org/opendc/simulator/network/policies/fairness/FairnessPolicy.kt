package org.opendc.simulator.network.policies.fairness

import org.opendc.simulator.network.flow.FlowHandler
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.flow.tracker.TrackerMode
import org.opendc.simulator.network.utils.logger

internal interface FairnessPolicy {

    fun FlowHandler.applyPolicy(updt: RateUpdt)

    /**
     * Executes the data rate reductions of the [updt].
     *
     * Every policy should always execute this method first,
     * since rate reductions are always possible and it frees
     * bandwidth for other flows rate increases.
     */
    fun FlowHandler.execRateReductions(updt: RateUpdt) {
        updt.forEach { fId, deltaRate ->
            if (deltaRate < 0) outgoingFlows[fId]?.tryUpdtRate()
        }
    }

    companion object {
        val log by logger()
        var VERIFY: Boolean = false
    }
}
