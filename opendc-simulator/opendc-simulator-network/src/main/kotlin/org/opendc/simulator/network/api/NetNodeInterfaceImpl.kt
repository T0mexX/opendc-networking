package org.opendc.simulator.network.api

import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.Kbps

internal class NetNodeInterfaceImpl(
    private val node: EndPointNode,
    private val netController: NetworkController
): NetNodeInterface {

    override val nodeId: NodeId = node.id

    override suspend fun startFlow(
        destinationId: NodeId?,
        desiredDataRate: Kbps,
        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
    ): NetFlow? =
        netController.startFlow(
            transmitterId = this.nodeId,
            destinationId = destinationId ?: INTERNET_ID,
            dataRateOnChangeHandler = throughputChangeHandler,
        )

    override suspend fun stopFlow(id: FlowId) {
        netController.stopFlow(id)
    }

    override fun getMyFlow(id: FlowId): NetFlow? =
        node.flowHandler.generatedFlows[id]

    override suspend fun fromInternet(
        desiredDataRate: Kbps,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
    ): NetFlow =
        netController.internetNetworkInterface.startFlow(
            destinationId = this.nodeId,
            desiredDataRate = desiredDataRate,
            throughputChangeHandler = dataRateOnChangeHandler,
        ) !!
}
