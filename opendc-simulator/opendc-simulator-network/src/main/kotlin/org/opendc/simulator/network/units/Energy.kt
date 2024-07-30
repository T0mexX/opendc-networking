package org.opendc.simulator.network.units

internal interface Energy<T: Unit<T>>: Unit<T> {
    fun whValue(): Double
    fun kWhValue(): Double
    fun toWh(): Wh
    fun toKWh(): KWh
}

@JvmInline
internal value class Wh(override val value: Double): Energy<Wh> {
    override fun whValue(): Double = value
    override fun kWhValue(): Double = value / 1000.0
    override fun toWh(): Wh = this
    override fun toKWh(): KWh = KWh(value / 1000.0)
    override fun new(value: Double): Wh = Wh(value)
    override fun toString(): String = "$value Wh"
}

@JvmInline
internal value class KWh(override val value: Double): Energy<KWh> {
    override fun whValue(): Double = value * 1000.0
    override fun kWhValue(): Double = value
    override fun toWh(): Wh = Wh(value * 1000.0)
    override fun toKWh(): KWh = this
    override fun new(value: Double): KWh = KWh(value)
    override fun toString(): String = "$value KWh"
}
