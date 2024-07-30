package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Energy<T>: Unit<T> where T : Unit<T>, T: Energy<T> {
    public fun whValue(): Double
    public fun kWhValue(): Double = whValue() / 1000.0
    public fun toWh(): Wh = Wh(whValue())
    public fun toKWh(): KWh = KWh(kWhValue())

    public fun <P: Energy<*>> P.convert(): T
    public operator fun <P: Energy<*>> plus(other: P): Energy<T> = super.plus(other.convert())

}

@JvmInline
@Serializable
public value class Wh(override val value: Double): Energy<Wh> {
    override fun whValue(): Double = value
    override fun new(value: Double): Wh = Wh(value)
    override fun <P : Energy<*>> P.convert(): Wh = this@convert.toWh()
    override fun toString(): String = "$value Wh"
}

@JvmInline
@Serializable
public value class KWh(override val value: Double): Energy<KWh> {
    override fun whValue(): Double = value * 1000.0
    override fun new(value: Double): KWh = KWh(value)
    override fun <P : Energy<*>> P.convert(): KWh = this@convert.toKWh()
    override fun toString(): String = "$value KWh"
}
