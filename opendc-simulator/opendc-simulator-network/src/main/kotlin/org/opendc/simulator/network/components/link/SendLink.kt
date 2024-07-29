package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps

internal interface SendLink: Link {
    val outgoingRatesById: Map<FlowId, Kbps>


    fun Port.updtFlowRate(fId: FlowId, rqstRate: Kbps): Kbps
    suspend fun notifyReceiver()

    fun outgoingRateOf(fId: FlowId): Kbps
}
