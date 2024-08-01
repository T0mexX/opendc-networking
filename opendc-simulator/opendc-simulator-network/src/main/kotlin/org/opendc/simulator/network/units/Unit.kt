package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable
import kotlin.math.abs


private const val dfltEpsilon: Double = 1.0e-05
private const val minErr: Double = 1.0e-06

@Serializable
public sealed interface Unit<T: Unit<T>> {
    public val value: Double


    public operator fun plus(other: T): T = new(value + other.value)
    public operator fun minus(other: T): T = new(value - other.value)
    public operator fun div(other: Number): T = new(value / other.toDouble())
    public operator fun times(other: Number): T = new(value * other.toDouble())
    public operator fun compareTo(other: T): Int = this.value.compareTo(other.value)

    public fun approxLarger(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > epsilon * maxOf(minErr, abs(this.value), abs(other.value))

    public fun approxLargerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > -epsilon * maxOf(minErr, abs(this.value), abs(other.value))

    public fun approxSmaller(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < -epsilon * maxOf(minErr, abs(this.value), abs(other.value))

    public fun approxSmallerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < epsilon * maxOf(minErr, abs(this.value), abs(other.value))


    public fun new(value: Double): T
}

public operator fun <P: Power<*>> P.times(time: Time<*>): Energy<*> = KWh(kWattsValue() * time.hoursValue())
public operator fun <T: Time<*>> T.times(power: Power<*>): Energy<*> = KWh(hoursValue() * power.kWattsValue())



public operator fun Energy<*>.div(time: Time<*>): Power<*> = KWatts(whValue() * time.hoursValue())
public operator fun <T: Unit<T>, N: Number> N.times(unit: T): T = unit * this
