package org.opendc.simulator.network.components.link

import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.units.DataRate

internal interface SendLink: Link {
    val outgoingRatesById: Map<FlowId, DataRate>


    fun Port.updtFlowRate(fId: FlowId, rqstRate: DataRate): DataRate
    suspend fun notifyReceiver()

    fun outgoingRateOf(fId: FlowId): DataRate
}
