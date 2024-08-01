package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Energy<T>: Unit<Energy<*>, T> where T: Energy<T> {
    public fun whValue(): Double
    public fun kWhValue(): Double = whValue() / 1000.0

    public fun toWh(): Wh = Wh(whValue())
    public fun toKWh(): KWh = KWh(kWhValue())

    public companion object { public val ZERO: Energy<*> = Wh(.0) }
}

@JvmInline
@Serializable
public value class Wh(override val value: Double): Energy<Wh> {
    override fun whValue(): Double = value
    override fun new(value: Double): Wh = Wh(value)
    override fun Energy<*>.convert(): Wh = this@convert.toWh()
    override fun toString(): String = "$value Wh"
}

@JvmInline
@Serializable
public value class KWh(override val value: Double): Energy<KWh> {
    override fun whValue(): Double = value * 1000.0
    override fun new(value: Double): KWh = KWh(value)
    override fun Energy<*>.convert(): KWh = this@convert.toKWh()
    override fun toString(): String = "$value KWh"
}
