package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Energy<T: Unit<T>>: Unit<T> {
    fun whValue(): Double
    fun kWhValue(): Double = whValue() / 1000.0
    fun toWh(): Wh = Wh(whValue())
    fun toKWh(): KWh = KWh(kWhValue())
}

@JvmInline
@Serializable
internal value class Wh(override val value: Double): Energy<Wh> {
    override fun whValue(): Double = value
    override fun new(value: Double): Wh = Wh(value)
    override fun toString(): String = "$value Wh"
}

@JvmInline
@Serializable
internal value class KWh(override val value: Double): Energy<KWh> {
    override fun whValue(): Double = value * 1000.0
    override fun new(value: Double): KWh = KWh(value)
    override fun toString(): String = "$value KWh"
}
