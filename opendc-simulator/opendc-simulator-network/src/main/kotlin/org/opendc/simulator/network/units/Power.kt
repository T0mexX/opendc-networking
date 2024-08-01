package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Power<T>: Unit<Power<*>, T> where T:Power<T> {
    public fun wattsValue(): Double
    public fun kWattsValue(): Double = wattsValue() / 1000.0

    public fun toWatts(): Watts = Watts(wattsValue())
    public fun toKWatts(): KWatts = KWatts(kWattsValue())

    public companion object { public val ZERO: Power<*> = Watts(.0) }
}

@JvmInline
@Serializable
public value class Watts(override val value: Double): Power<Watts> {
    override fun wattsValue(): Double = value
    override fun toWatts(): Watts = this
    override fun new(value: Double): Watts = Watts(value)
    override fun Power<*>.convert(): Watts = this@convert.toWatts()
    override fun toString(): String = "$value W"
}

@JvmInline
@Serializable
public value class KWatts(override val value: Double): Power<KWatts> {
    override fun wattsValue(): Double = value * 1000.0
    override fun toKWatts(): KWatts = this
    override fun new(value: Double): KWatts = KWatts(value)
    override fun Power<*>.convert(): KWatts = this@convert.toKWatts()
    override fun toString(): String = "$value KW"
}




