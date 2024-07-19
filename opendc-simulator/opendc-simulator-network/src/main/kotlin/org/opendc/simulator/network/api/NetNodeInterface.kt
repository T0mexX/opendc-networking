package org.opendc.simulator.network.api

import org.opendc.simulator.network.components.Internet
import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.Kbps


/**
 * Interface that can be used by other modules
 * to control the networking of a single node.
 */
public interface NetNodeInterface {

    /**
     * Physical id associated with this node.
     */
    public val nodeId: NodeId

    /**
     * Starts a network flow ([NetFlow]) from this node to the node with id [destinationId].
     *
     * If [destinationId] is null than the flow is directed to the [Internet].
     *
     * @param[destinationId]                the id of the destination node.
     * @param[desiredDataRate]              the initial data rate of the new flow.
     * @param[throughputChangeHandler]      a function that is invoked whenever the
     * throughput of the flow changes. The function second parameter is the old value,
     * while the third one is the new value.
     *
     * @return  a handle on the flow. One is able
     * to adjust the desired data rate modifying [NetFlow.desiredDataRate].
     */
    public suspend fun startFlow(
        destinationId: NodeId? = null,
        desiredDataRate: Kbps = .0,
        throughputChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow?

    /**
     * Stops the flow with id [id] if it exists, and it belongs to this node.
     */
    public suspend fun stopFlow(id: FlowId)

    /**
     * @return a handle on the flow with id [id] if it exists, and it belongs to this node.
     */
    public fun getMyFlow(id: FlowId): NetFlow?

    /**
     * Starts a flow from the [Internet] to this node.
     * Alternatively one can use [NetworkController.internetNetworkInterface].
     *
     * See [startFlow] for parameters docs.
     */
    public suspend fun fromInternet(
        desiredDataRate: Kbps = .0,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
    ): NetFlow
}
