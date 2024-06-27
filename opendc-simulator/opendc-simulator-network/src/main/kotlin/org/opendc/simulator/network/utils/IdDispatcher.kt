package org.opendc.simulator.network.utils

import org.opendc.simulator.network.components.NodeId
import org.opendc.simulator.network.components.Node

/**
 * Used to automatically assign ids to [Node]s.
 */
internal object IdDispatcher {
    var nextId: NodeId = 0
        get() {
            field++
            return field - 1
        }
}
