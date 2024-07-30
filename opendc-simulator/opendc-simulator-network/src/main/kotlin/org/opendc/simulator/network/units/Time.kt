package org.opendc.simulator.network.units

internal interface Time<T: Unit<T>>: Unit<T> {
    fun msValue(): Double
    fun secValue(): Double
    fun hoursValue(): Double
    fun toMs(): Ms = Ms(msValue())
    fun toSec(): Seconds = Seconds(secValue())
    fun toHours(): Hours = Hours(hoursValue())
}

@JvmInline
internal value class Ms(override val value: Double): Time<Ms> {
    override fun msValue(): Double = value
    override fun secValue(): Double = value / 1000.0
    override fun hoursValue(): Double = value * 1000 * 60 * 60
    override fun toMs(): Ms = this
    override fun new(value: Double): Ms = Ms(value)
    override fun toString(): String = "$value ms"
}

@JvmInline
internal value class Seconds(override val value: Double): Time<Seconds> {
    override fun msValue(): Double = value * 1000.0
    override fun secValue(): Double = value
    override fun hoursValue(): Double = value * 60 * 60
    override fun toSec(): Seconds = this
    override fun new(value: Double): Seconds = Seconds(value)
    override fun toString(): String = "$value s"
}


@JvmInline
internal value class Hours(override val value: Double): Time<Hours> {
    override fun msValue(): Double = value * 60 * 60 * 1000
    override fun secValue(): Double = value * 60 * 60
    override fun hoursValue(): Double = value
    override fun toHours(): Hours = this
    override fun new(value: Double): Hours = Hours(value)
    override fun toString(): String = "$value h"
}
