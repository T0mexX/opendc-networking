@file:OptIn(InternalUse::class)

package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.utils.InternalUse
import kotlin.math.abs


internal const val dfltEpsilon: Double = 1.0e-05
private const val minErr: Double = 1.0e-06

@Serializable
public sealed interface Unit<T: Unit<T>> {
    public val value: Double

    public operator fun plus(other: T): T = new(value + other.value)

    public operator fun minus(other: T): T = new(value - other.value)

    public operator fun div(other: Number): T = new(value / other.toDouble())
    public operator fun times(other: Number): T = new(value * other.toDouble())

    public operator fun compareTo(other: T): Int = this.value.compareTo(other.value)

    public fun approx(other: T, epsilon: Double = dfltEpsilon): Boolean =
        this == other || abs((this - other).value) <= epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approx(other: T): Boolean = approx(other)

    public fun approxLarger(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxLarger(other: T): Boolean = approxLarger(other)

    public fun approxLargerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > -epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxLargerOrEqual(other: T): Boolean = approxLargerOrEqual(other)

    public fun approxSmaller(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < -epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxSmaller(other: T): Boolean = approxSmaller(other)

    public fun approxSmallerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxSmallerOrEqual(other: T): Boolean = approxSmallerOrEqual(other)

    @Suppress("UNCHECKED_CAST")
    public fun roundedTo0WithEps(epsilon: Double = dfltEpsilon): T =
        if (this.value in (-epsilon..epsilon)) new(.0)
        else this as T

    @InternalUse
    public fun new(value: Double): T
}

public operator fun Power.times(time: Time): Energy = Energy.ofWh(toWatts() * time.toHours())
public operator fun Time.times(power: Power): Energy = Energy.ofWh(toHours() * power.toWatts())

public operator fun DataRate.times(time: Time): Data = Data.ofKB(toKBps() * time.toSec())
public operator fun Time.times(dataRate: DataRate): Data = Data.ofKB(toSec() * dataRate.toKBps())

public operator fun Energy.div(time: Time): Power = Power.ofWatts(toWh() / time.toHours())

public operator fun <T: Unit<T>> Number.times(unit: T): T = unit * this
