package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

internal interface ReceiveLink: Link {
    val incomingRateById:Map<FlowId, Kbps>

    fun incomingRateOf(fId: FlowId): Kbps
}
