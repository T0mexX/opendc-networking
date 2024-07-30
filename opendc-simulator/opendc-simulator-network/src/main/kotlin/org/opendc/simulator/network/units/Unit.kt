package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable


@Serializable
public sealed interface Unit<T: Unit<T>> {
    public val value: Double

    public operator fun plus(other: T): T = new(value + other.value)
    public operator fun minus(other: T): T = new(value - other.value)
    public operator fun div(other: Number): T = new(value / other.toDouble())
    public operator fun times(other: Number): T = new(value * other.toDouble())
    public fun new(value: Double): T
}

public operator fun <P: Power<*>> P.times(time: Time<*>): Energy<*> = KWh(kWattsValue() * time.hoursValue())
public operator fun <T: Time<*>> T.times(power: Power<*>): Energy<*> = KWh(hoursValue() * power.kWattsValue())

public operator fun Energy<*>.div(time: Time<*>): Power<*> = KWatts(whValue() * time.hoursValue())
public operator fun <T: Unit<T>, N: Number> N.times(unit: T): T = unit * this
