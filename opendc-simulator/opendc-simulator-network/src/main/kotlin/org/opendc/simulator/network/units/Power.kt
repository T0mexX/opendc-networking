package org.opendc.simulator.network.units

import org.opendc.simulator.network.utils.InternalUse

@JvmInline
public value class Power private constructor (
    override val value: Double
): Unit<Power> {

    @InternalUse
    override fun new(value: Double): Power = Power(value)

    public fun toWatts(): Double = value
    public fun toKWatts(): Double = value / 1000.0

    override fun toString(): String =
        if (value >= 1000.0) "${toKWatts()} KWatts"
        else "${toWatts()} Watts"

    public companion object {
        public val ZERO: Power = Power(.0)
        public fun ofWatts(watts: Number): Power = Power(watts.toDouble())
        public fun ofKWatts(kWatts: Number): Power = Power(kWatts.toDouble() * 1000.0)
    }
}
