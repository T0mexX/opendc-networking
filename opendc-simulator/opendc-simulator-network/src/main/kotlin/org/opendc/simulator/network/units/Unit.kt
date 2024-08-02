package org.opendc.simulator.network.units

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.math.abs


internal const val dfltEpsilon: Double = 1.0e-05
private const val minErr: Double = 1.0e-06

@Serializable
public sealed interface Unit<S, T: Unit<S, T>> {
    public val value: Double

    public fun S.convert(): T


    public operator fun plus(other: T): T = new(value + other.value)
    public operator fun plus(other: S): T = new(value + other.convert().value)

    public operator fun minus(other: T): T = new(value - other.value)
    public operator fun minus(other: S): T = new(value - other.convert().value)

    public operator fun div(other: Number): T = new(value / other.toDouble())
    public operator fun times(other: Number): T = new(value * other.toDouble())

    public operator fun compareTo(other: T): Int = this.value.compareTo(other.value)
    public operator fun compareTo(other: S): Int = this.compareTo(other.convert())

    public fun approx(other: T, epsilon: Double = dfltEpsilon): Boolean =
        this == other || abs((this - other).value) <= epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approx(other: T): Boolean = approx(other)
    public infix fun approx(other: S): Boolean = approx(other.convert())

    public fun approxLarger(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxLarger(other: T): Boolean = approxLarger(other)
    public infix fun approxLarger(other: S): Boolean = approxLarger(other.convert())

    public fun approxLargerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > -epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxLargerOrEqual(other: T): Boolean = approxLargerOrEqual(other)
    public infix fun approxLargerOrEqual(other: S): Boolean = approxLargerOrEqual(other.convert())

    public fun approxSmaller(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < -epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxSmaller(other: T): Boolean = approxSmaller(other)
    public infix fun approxSmaller(other: S): Boolean = approxSmaller(other.convert())

    public fun approxSmallerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxSmallerOrEqual(other: T): Boolean = approxSmallerOrEqual(other)
    public infix fun approxSmallerOrEqual(other: S): Boolean = approxSmallerOrEqual(other.convert())

    @Suppress("UNCHECKED_CAST")
    public fun roundedTo0WithEps(epsilon: Double = dfltEpsilon): T =
        if (this.value in (-epsilon..epsilon)) new(.0)
        else this as T

    public fun new(value: Double): T
}

public operator fun <P: Power<*>> P.times(time: Time<*>): Energy<*> = KWh(kWattsValue() * time.hoursValue())
public operator fun <T: Time<*>> T.times(power: Power<*>): Energy<*> = KWh(hoursValue() * power.kWattsValue())

public operator fun <DR: DataRate<*>> DR.times(time: Time<*>): Data<*> = MB(mBpsValue() * time.secValue())
public operator fun <T: Time<*>> T.times(dataRate: DataRate<*>): Data<*> = MB(secValue() * dataRate.mBpsValue())

public operator fun Energy<*>.div(time: Time<*>): Power<*> = KWatts(whValue() * time.hoursValue())

public operator fun <T: Unit<*, T>, N: Number> N.times(unit: T): T = unit * this
