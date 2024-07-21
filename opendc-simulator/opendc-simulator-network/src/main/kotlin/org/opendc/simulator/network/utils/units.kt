package org.opendc.simulator.network.utils

import kotlin.math.abs
import kotlin.math.max

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



internal fun Double.roundTo0ifErr(err: Double = 0.00001): Double =
    if (this < .0 && -this < err) .0
    else this

private val dfltEpsilon: Double = 1.0e-05
internal fun Double.approx(other: Double, epsilon: Double = dfltEpsilon ): Boolean =
    this == other || abs(this - other) <= epsilon * max(abs(this), abs(other))

internal fun Double.approxLarger(other: Double, epsilon: Double = dfltEpsilon): Boolean =
    (this - other) > epsilon * max(abs(this), abs(other))



