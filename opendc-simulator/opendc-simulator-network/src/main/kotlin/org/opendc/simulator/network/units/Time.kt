package org.opendc.simulator.network.units

import org.opendc.simulator.network.utils.InternalUse
import java.time.Duration
import java.time.Instant

@JvmInline
public value class Time private constructor(
    override val value: Double
): Unit<Time> {

    @InternalUse
    override fun new(value: Double): Time = Time(value)

    public fun toMs(): Double = value
    public fun toMsLong(): Long = value.toLong()
    public fun toSec(): Double = value / 1000.0
    public fun toMin(): Double = toSec() / 60
    public fun toHours(): Double = toMin() / 60
    public fun toInstantFromEpoch(): Instant = Instant.ofEpochMilli(value.toLong())

    override fun toString(): String = fmtValue()

    /**
     * No ops.
     */
    override fun fmtValue(fmt: String): String =
        Duration.ofMillis(value.toLong()).toString()

    public operator fun times(power: Power): Energy = Energy.ofWh(toHours() * power.toWatts())

    public operator fun times(dataRate: DataRate): Data = Data.ofKB(toSec() * dataRate.toKBps())

    public companion object {
        @JvmStatic public val ZERO: Time = Time(.0)

        @JvmStatic public fun ofMillis(ms: Number): Time = Time(ms.toDouble())
        @JvmStatic public fun ofSec(sec: Number): Time = Time(sec.toDouble() * 1000.0)
        @JvmStatic public fun ofMin(min: Number): Time = Time(min.toDouble() * 60 * 1000.0)
        @JvmStatic public fun ofHours(hours: Number): Time = Time(hours.toDouble() * 60 * 60 * 1000.0)
        @JvmStatic public fun ofDuration(dur: Duration): Time = Time.ofMillis(dur.toMillis())
        @JvmStatic public fun ofInstantFromEpoch(instant: Instant): Time = Time.ofMillis(instant.toEpochMilli())
    }
}
