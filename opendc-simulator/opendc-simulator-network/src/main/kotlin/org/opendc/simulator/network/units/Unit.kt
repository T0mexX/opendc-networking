@file:OptIn(InternalUse::class)

package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Range
import org.opendc.simulator.network.utils.InternalUse
import org.opendc.simulator.network.utils.approx
import kotlin.math.abs


internal const val dfltEpsilon: Double = 1.0e-05
private const val minErr: Double = 1.0e-06

@Serializable
public sealed interface Unit<T: Unit<T>>: Comparable<T> {
    public val value: Double

    public operator fun plus(other: T): T = new(value + other.value)

    public operator fun minus(other: T): T = new(value - other.value)

    public operator fun div(other: Number): T = new(value / other.toDouble())
    public operator fun div(other: T): Double = value / other.value

    public operator fun times(other: Number): T = new(value * other.toDouble())

    public operator fun unaryMinus(): T = new(-value)

    public override operator fun compareTo(other: T): Int = this.value.compareTo(other.value)

    public fun isZero(): Boolean = value == .0
    public fun approxZero(epsilon: Double = dfltEpsilon): Boolean =
        value.approx(.0, epsilon = epsilon)

    public fun approx(other: T, epsilon: Double = dfltEpsilon): Boolean =
        this == other || abs((this - other).value) <= epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approx(other: T): Boolean = approx(other, dfltEpsilon)

    public fun approxLarger(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxLarger(other: T): Boolean = approxLarger(other, dfltEpsilon)

    public fun approxLargerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value > -epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxLargerOrEqual(other: T): Boolean = approxLargerOrEqual(other, dfltEpsilon)

    public fun approxSmaller(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < -epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxSmaller(other: T): Boolean = approxSmaller(other, dfltEpsilon)

    public fun approxSmallerOrEqual(other: T, epsilon: Double = dfltEpsilon): Boolean =
        (this - other).value < epsilon * maxOf(minErr, abs(this.value), abs(other.value))
    public infix fun approxSmallerOrEqual(other: T): Boolean = approxSmallerOrEqual(other, dfltEpsilon)

    @Suppress("UNCHECKED_CAST")
    public infix fun min(other: T): T = if (this.value < other.value) this as T else other

    @Suppress("UNCHECKED_CAST")
    public infix fun max(other: T): T = if (this.value > other.value) this as T else other

    @Suppress("UNCHECKED_CAST")
    public fun roundedTo0WithEps(epsilon: Double = dfltEpsilon): T =
        if (this.value in (-epsilon..epsilon)) new(.0)
        else this as T

    @InternalUse
    public fun new(value: Double): T
}


public operator fun <T: Unit<T>> Number.times(unit: T): T = unit * this

public fun <T: Unit<T>> min(a: T, b: T): T = if (a.value < b.value) a else b
public fun <T: Unit<T>> minOf(vararg units: T): T = units.minBy { it.value }

public fun <T: Unit<T>> max(a: T, b: T): T = if (a.value > b.value) a else b
public fun <T: Unit<T>> maxOf(vararg units: T): T = units.maxBy { it.value }

