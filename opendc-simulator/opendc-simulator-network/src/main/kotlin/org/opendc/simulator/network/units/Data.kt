package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Data<T>: Unit<Data<*>, T> where T: Data<T> {
    public fun kBValue(): Double
    public fun mBValue(): Double = kBValue() / 1024
    public fun gBValue(): Double = mBValue() / 1024

    public fun toKB(): KB = KB(kBValue())
    public fun toMB(): MB = MB(mBValue())
    public fun toGB(): GB = GB(gBValue())

    public companion object { public val ZERO: Data<*> = KB(.0) }
}

@JvmInline
@Serializable
public value class KB(override val value: Double): Data<KB> {
    override fun kBValue(): Double = value
    override fun Data<*>.convert(): KB = this@convert.toKB()
    override fun new(value: Double): KB = KB(value)
    override fun toString(): String = "$value KB"
}

@JvmInline
@Serializable
public value class MB(override val value: Double): Data<MB> {
    override fun kBValue(): Double = value * 1024
    override fun Data<*>.convert(): MB = this@convert.toMB()
    override fun new(value: Double): MB = MB(value)
    override fun toString(): String = "$value MB"
}

@JvmInline
@Serializable
public value class GB(override val value: Double): Data<GB> {
    override fun kBValue(): Double = value * 1024 * 1024
    override fun Data<*>.convert(): GB = this@convert.toGB()
    override fun new(value: Double): GB = GB(value)
    override fun toString(): String = "$value GB"
}
