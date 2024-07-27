package org.opendc.simulator.network.utils

import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Node

/**
 * Used to automatically assign ids to [Node]s.
 */
internal object IdDispenser {
    var nextNodeId: NodeId = 0
        get() {
            field++
            return field - 1
        }
        private set

    var nextFlowId: Int = 0
        get() {
            field++
            return field - 1
        }
        private set
}
