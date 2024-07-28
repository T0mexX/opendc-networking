package org.opendc.simulator.network.utils

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal typealias Watts = Double

internal typealias Mbps = Double
internal typealias Mb = Double
internal typealias MBps = Double
internal typealias MB = Double
internal typealias Gbps = Double
internal typealias Gb = Double
internal typealias GBps = Double
internal typealias GB = Double
internal typealias Kbps = Double
internal typealias Kb = Double
internal typealias KBps = Double
internal typealias KB = Double
internal typealias ms = Long

internal fun Double.toLowerDataUnit(): Double = this * 1024
internal fun Double.toHigherDataUnit(): Double = this / 1024



internal fun Double.roundTo0withEps(err: Double = 0.00001): Double =
    if (this in (-err..err)) .0
    else this

private val dfltEpsilon: Double = 1.0e-05
private val minErr: Double = 1.0e-06

internal fun Double.approx(other: Double, epsilon: Double = dfltEpsilon): Boolean =
    this == other || abs(this - other) <= epsilon * maxOf(minErr, abs(this), abs(other))

internal infix fun Double.approx(other: Double): Boolean =
    approx(other, epsilon = dfltEpsilon)



internal fun Double.approxLarger(other: Double, epsilon: Double = dfltEpsilon): Boolean =
    (this - other) > epsilon * maxOf(minErr, abs(this), abs(other))

internal infix fun Double.approxLarger(other: Double): Boolean =
    this.approxLarger(other, epsilon = dfltEpsilon)

internal infix fun Double.ifNanThen(default: Double): Double =
    if (this.isNaN()) default else this

internal inline infix fun <T> Double.ifNaN(block: () -> Double): Double =
    if (this.isNaN()) block()
    else this



