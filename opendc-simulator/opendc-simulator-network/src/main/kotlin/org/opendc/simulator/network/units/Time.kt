package org.opendc.simulator.network.units

import org.opendc.simulator.network.utils.InternalUse
import java.time.Duration

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

    override fun toString(): String =
        Duration.ofMillis(value.toLong()).toString()

    public companion object {
        public val ZERO: Time = Time(.0)

        public fun ofMillis(ms: Number): Time = Time(ms.toDouble())
        public fun ofSec(sec: Number): Time = Time(sec.toDouble() * 1000.0)
        public fun ofMin(min: Number): Time = Time(min.toDouble() * 60 * 1000.0)
        public fun ofHours(hours: Number): Time = Time(hours.toDouble() * 60 * 60 * 1000.0)
    }
}
