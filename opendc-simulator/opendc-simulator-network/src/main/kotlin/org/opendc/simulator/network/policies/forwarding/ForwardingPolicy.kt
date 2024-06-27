package org.opendc.simulator.network.policies.forwarding

import org.opendc.simulator.network.flow.Flow
import org.opendc.simulator.network.components.Node

/**
 * A policy that determines how a [Node] forwards incoming [Flow]s
 */
internal fun interface ForwardingPolicy {
    fun forwardFlow(forwarder: Node, flow: Flow)
}
