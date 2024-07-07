package org.opendc.simulator.network.interfaces

import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.IdDispenser
import org.opendc.simulator.network.utils.Kbps

public interface NetNodeInterface {
    public val nodeId: NodeId
    public fun startFlow(
        destinationId: NodeId? = null,
        desiredDataRate: Kbps = .0,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null
    ): NetFlow?

    public fun stopFlow(id: FlowId)

    public fun getMyFlow(id: FlowId): NetFlow?

    public fun fromInternet(
        desiredDataRate: Kbps = .0,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)?
    ): NetFlow
}
