package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Power<T: Unit<T>>: Unit<T> {
    fun wattsValue(): Double
    fun kWattsValue(): Double = wattsValue() / 1000.0
    fun toWatts(): Watts = Watts(wattsValue())
    fun toKWatts(): KWatts = KWatts(kWattsValue())

}

@JvmInline
@Serializable
internal value class Watts(override val value: Double): Power<Watts> {
    override fun wattsValue(): Double = value
    override fun toWatts(): Watts = this
    override fun new(value: Double): Watts = Watts(value)
    override fun toString(): String = "$value W"
}

@JvmInline
@Serializable
internal value class KWatts(override val value: Double): Power<KWatts> {
    override fun wattsValue(): Double = value * 1000.0
    override fun toKWatts(): KWatts = this
    override fun new(value: Double): KWatts = KWatts(value)
    override fun toString(): String = "$value KW"
}




