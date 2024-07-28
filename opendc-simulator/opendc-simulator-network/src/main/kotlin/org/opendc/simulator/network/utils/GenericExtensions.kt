package org.opendc.simulator.network.utils


internal fun <T: Comparable<T>> Collection<T>.isSorted(): Boolean {
    if (this.isEmpty()) return true

    var prev: T = this.first()
    this.forEach { curr: T ->
        if (curr < prev) return false
        prev = curr
    }

    return true
}

internal fun <T> Collection<T>.isSorted(comp: Comparator<T>): Boolean {
    if (this.isEmpty()) return true

    var prev: T = this.first()
    this.forEach { curr: T ->
        if (curr.smallerThan(prev, comp)) return false
        prev = curr
    }

    return true
}

private fun <T> T.smallerThan(other: T, comp: Comparator<T>): Boolean =
    comp.compare(this, other) < 0
