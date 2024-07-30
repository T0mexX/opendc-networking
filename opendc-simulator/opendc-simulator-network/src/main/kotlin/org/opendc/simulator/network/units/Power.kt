package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Power<T>: Unit<T> where T : Unit<T>, T:Power<T>{
    public fun wattsValue(): Double
    public fun kWattsValue(): Double = wattsValue() / 1000.0
    public fun toWatts(): Watts = Watts(wattsValue())
    public fun toKWatts(): KWatts = KWatts(kWattsValue())


    public fun <P: Power<*>> P.convert(): T
    public operator fun <P: Power<*>> plus(other: P): Power<T> =
        super.plus(other.convert())
}

@JvmInline
@Serializable
public value class Watts(override val value: Double): Power<Watts> {
    override fun wattsValue(): Double = value
    override fun toWatts(): Watts = this
    override fun new(value: Double): Watts = Watts(value)
    override fun <P : Power<*>> P.convert(): Watts = this@convert.toWatts()
    override fun toString(): String = "$value W"
}

@JvmInline
@Serializable
public value class KWatts(override val value: Double): Power<KWatts> {
    override fun wattsValue(): Double = value * 1000.0
    override fun toKWatts(): KWatts = this
    override fun new(value: Double): KWatts = KWatts(value)
    override fun <P : Power<*>> P.convert(): KWatts = this@convert.toKWatts()
    override fun toString(): String = "$value KW"
}




