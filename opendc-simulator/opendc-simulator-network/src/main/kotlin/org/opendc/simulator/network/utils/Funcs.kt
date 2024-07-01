package org.opendc.simulator.network.utils

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal fun <T : Any> Delegates.writeOnce(): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    private var value: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: throw IllegalStateException("Property ${property.name} should be initialized before get.")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        check (this.value != null) { IllegalStateException("Property ${property.name} cannot be set more than once.") }
        this.value = value
    }
}

internal fun Double.largerThanBy(other: Double, deltaPerc: Double): Boolean =
    this / other - 1 < deltaPerc
