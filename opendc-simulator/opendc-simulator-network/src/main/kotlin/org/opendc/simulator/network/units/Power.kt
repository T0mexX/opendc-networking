package org.opendc.simulator.network.units

internal interface Power<T: Unit<T>>: Unit<T> {
    fun wattsValue(): Double
    fun kWattsValue(): Double
    fun toWatts(): Watts
    fun toKWatts(): KWatts

}
@JvmInline
internal value class Watts(override val value: Double): Power<Watts> {
    override fun wattsValue(): Double = value
    override fun kWattsValue(): Double = value / 1000.0
    override fun toWatts(): Watts = this
    override fun toKWatts(): KWatts = KWatts(value / 1000.0)
    override fun new(value: Double): Watts = Watts(value)
    override fun toString(): String = "$value W"
}

@JvmInline
internal value class KWatts(override val value: Double): Power<KWatts> {
    override fun wattsValue(): Double = value * 1000.0
    override fun kWattsValue(): Double = value
    override fun toWatts(): Watts = Watts(value * 1000.0)
    override fun toKWatts(): KWatts = this
    override fun new(value: Double): KWatts = KWatts(value)
    override fun toString(): String = "$value KW"
}




