package org.opendc.simulator.network.input

import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.flow.FlowId
import kotlin.properties.Delegates

/**
 * Contains all the information needed to create a network event **except** knowledge about previous events.
 * Traces are first converted to event imprints and then to [SimNetWorkload] after they have been ordered by timestamp,
 * since dataframe offer no guarantee on the order of fragments.
 */
internal data class NetEventImprint(
    val deadline: Time,
    val transmitterId: NodeId,
    val destId: NodeId,
    val netTx: DataRate,
    val flowId: FlowId? = null,
    val duration: Time? = null,
) {
    class Builder {
        var deadline by Delegates.notNull<Time>()
        var transmitterId by Delegates.notNull<NodeId>()
        var destId by Delegates.notNull<NodeId>()
        var netTx by Delegates.notNull<DataRate>()
        var flowId: FlowId? = null
        var duration: Time? = null

        fun build(): NetEventImprint =
            NetEventImprint(
                deadline = deadline,
                transmitterId = transmitterId,
                destId = destId,
                netTx = netTx,
                flowId = flowId,
                duration = duration
            )

        fun reset() {
            flowId = null
            duration = null
            transmitterId = INTERNET_ID
            destId = INTERNET_ID
        }
    }
}
