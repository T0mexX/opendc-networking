package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.units.DataRate

internal interface ReceiveLink: Link {
    val incomingRateById:Map<FlowId, DataRate>

    fun incomingRateOf(fId: FlowId): DataRate
}
