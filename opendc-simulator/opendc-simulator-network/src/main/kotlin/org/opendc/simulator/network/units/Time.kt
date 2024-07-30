package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Time<T: Unit<T>>: Unit<T> {
    fun msValue(): Double
    fun secValue(): Double = msValue() / 1000.0
    fun hoursValue(): Double = secValue() / 60 / 60
    fun toMs(): Ms = Ms(msValue())
    fun toSec(): Seconds = Seconds(secValue())
    fun toHours(): Hours = Hours(hoursValue())
}

@JvmInline
@Serializable
internal value class Ms(override val value: Double): Time<Ms> {
    override fun msValue(): Double = value
    override fun toMs(): Ms = this
    override fun new(value: Double): Ms = Ms(value)
    override fun toString(): String = "$value ms"
}

@JvmInline
@Serializable
internal value class Seconds(override val value: Double): Time<Seconds> {
    override fun msValue(): Double = value * 1000.0
    override fun toSec(): Seconds = this
    override fun new(value: Double): Seconds = Seconds(value)
    override fun toString(): String = "$value s"
}


@JvmInline
@Serializable
internal value class Hours(override val value: Double): Time<Hours> {
    override fun msValue(): Double = value * 60 * 60 * 1000
    override fun toHours(): Hours = this
    override fun new(value: Double): Hours = Hours(value)
    override fun toString(): String = "$value h"
}
