package org.opendc.simulator.network.utils

internal fun interface OnChangeHandler<in T, R> {
    fun handleChange(obj: T, oldValue: R, newValue: R)
}

internal fun interface SuspOnChangeHandler<in T, R> {
    suspend fun handleChange(obj: T, olvValue: R, newValue: R)
}
