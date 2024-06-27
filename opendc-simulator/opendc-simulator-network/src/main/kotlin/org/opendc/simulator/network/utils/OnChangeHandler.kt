package org.opendc.simulator.network.utils

internal fun interface OnChangeHandler<in T, M> {
    fun handleChange(obj: T, oldValue: M, newValue: M)
}
