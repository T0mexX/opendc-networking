package org.opendc.simulator.network.units

import org.opendc.simulator.network.utils.InternalUse
import org.opendc.simulator.network.utils.fmt

@JvmInline
public value class Power private constructor (
    override val value: Double
): Unit<Power> {

    @InternalUse
    override fun new(value: Double): Power = Power(value)

    public fun toWatts(): Double = value
    public fun toKWatts(): Double = value / 1000.0

    override fun toString(): String = fmtValue()

    override fun fmtValue(fmt: String): String =
        if (value >= 1000.0) "${toKWatts().fmt(fmt)} KWatts"
        else "${toWatts().fmt(fmt)} Watts"



    public operator fun times(time: Time): Energy = Energy.ofWh(toWatts() * time.toHours())

    public companion object {
        @JvmStatic public val ZERO: Power = Power(.0)
        @JvmStatic public fun ofWatts(watts: Number): Power = Power(watts.toDouble())
        @JvmStatic public fun ofKWatts(kWatts: Number): Power = Power(kWatts.toDouble() * 1000.0)
    }
}
