package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable


@Serializable
internal sealed interface Unit<T: Unit<T>> {
    val value: Double

    operator fun plus(other: T): T = new(value + other.value)
    operator fun minus(other: T): T = new(value - other.value)
    operator fun div(other: Number): T = new(value / other.toDouble())
    operator fun times(other: Number): T = new(value * other.toDouble())

    fun new(value: Double): T

    companion object {
        operator fun Watts.div(time: Time<*>): Wh = Wh(value / time.hoursValue())
        operator fun KWatts.div(time: Time<*>): KWh = KWh(value / time.hoursValue())
        operator fun Power<*>.div(time: Time<*>): Energy<*> = KWh(kWattsValue() / time.hoursValue())

        operator fun Energy<*>.times(time: Time<*>): Power<*> = KWatts(whValue() * time.hoursValue())
    }
}

