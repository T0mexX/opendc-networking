package org.opendc.simulator.network.components.internalstructs.port

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.components.link.ReceiveLink
import org.opendc.simulator.network.components.link.SendLink
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.utils.logger

internal interface Port {

    val maxSpeed: DataRate
    val owner: Node

    var sendLink: SendLink?
    var receiveLink: ReceiveLink?



    /**
     * `true` if this port is connected in any direction to another port, `false` otherwise.
     */
    val isConnected: Boolean
    val incomingRatesById: Map<FlowId, DataRate>
    val outgoingRatesById: Map<FlowId, DataRate>
    val maxPortToPortBW: DataRate
    val nodeUpdtChl: UpdateChl
    val isActive: Boolean
    val util: Double
    val currSpeed: DataRate
    val otherEndPort: Port?
    val otherEndNode: Node?


    suspend fun notifyReceiver()
    fun incomingRateOf(fId: FlowId): DataRate
    fun outgoingRateOf(fId: FlowId): DataRate

    /**
     * Tries to update the flow corresponding to [fId] to the requested [targetRate].
     * @return  the actual data rate achieved for the flow.
     */
    fun tryUpdtRateOf(fId: FlowId, targetRate: DataRate): DataRate
}
