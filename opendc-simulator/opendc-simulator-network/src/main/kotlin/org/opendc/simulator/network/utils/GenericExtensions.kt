package org.opendc.simulator.network.utils

import org.jetbrains.annotations.TestOnly

@TestOnly
internal fun <T: Comparable<T>> Collection<T>.isSorted(): Boolean {
    if (this.isEmpty()) return true

    var prev: T = this.first()
    this.forEach { curr: T ->
        if (curr < prev) return false
        prev = curr
    }

    return true
}
