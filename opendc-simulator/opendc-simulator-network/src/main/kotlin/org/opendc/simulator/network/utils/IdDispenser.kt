package org.opendc.simulator.network.utils

import org.opendc.simulator.network.components.Node

/**
 * Used to automatically assign ids to [Node]s.
 */
internal class IdDispenser {
    var nextId: Int = 0
        get() {
            field++
            return field - 1
        }
        private set

    companion object {
        var nextStatic: Long = 0
            get() {
                field++
                return field - 1
            }
            private set
    }
}
