package org.opendc.simulator.network.units

import org.opendc.simulator.network.utils.InternalUse

@JvmInline
public value class Energy private constructor(
    override val value: Double
): Unit<Energy> {

    @InternalUse
    override fun new(value: Double): Energy = Energy(value)

    public fun toWh(): Double = value
    public fun toKWh(): Double = value / 1000.0

    override fun toString(): String =
        if (value >= 1000.0) "${toKWh()} KWh"
        else "${toWh()} Wh"

    public operator fun div(time: Time): Power = Power.ofWatts(toWh() / time.toHours())

    public companion object {
        @JvmStatic public val ZERO: Energy = Energy(.0)

        @JvmStatic public fun ofWh(wh: Number): Energy = Energy(wh.toDouble())
        @JvmStatic public fun ofKWh(kWh: Number): Energy = Energy(kWh.toDouble() * 1000.0)
    }
}
