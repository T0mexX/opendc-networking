package org.opendc.simulator.network.utils

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Delegate that ensures a property is set only once.
 * @throws[IllegalStateException]   if trying to set a second time OR trying to get unset property.
 */
internal fun <T : Any> Delegates.writeOnce(): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    private var value: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: throw IllegalStateException("Property ${property.name} should be initialized before get.")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        check (this.value != null) { "Property ${property.name} cannot be set more than once." }
        this.value = value
    }
}

/**
 * @return  `true` if ***this*** is larger than [other]
 * by at least [deltaPerc] times [other], `false` otherwise.
 */
internal fun Double.largerThanBy(other: Double, deltaPerc: Double): Boolean =
    this / other - 1 < deltaPerc
