package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Data<T>: Unit<T> where T : Unit<T>, T: Data<T> {
    public fun kBValue(): Double
    public fun mBValue(): Double = kBValue() / 1024
    public fun gBValue(): Double = mBValue() / 1024

    public fun toKB(): KB = KB(kBValue())
    public fun toMB(): MB = MB(mBValue())
    public fun toGB(): GB = GB(gBValue())

    public fun <P: Data<*>> P.convert(): T

    public operator fun <P: Data<*>> plus(other: P): Data<T> = super.plus(other.convert())
    public operator fun <P: Data<*>> minus(other: P): Data<T> = super.minus(other.convert())
    public operator fun <P: Data<*>> compareTo(other: P): Int = super.compareTo(other.convert())
}

@JvmInline
@Serializable
public value class KB(override val value: Double): Data<KB> {
    override fun kBValue(): Double = value
    override fun <P : Data<*>> P.convert(): KB = this@convert.toKB()
    override fun new(value: Double): KB = KB(value)
}

@JvmInline
@Serializable
public value class MB(override val value: Double): Data<MB> {
    override fun kBValue(): Double = value * 1024
    override fun <P : Data<*>> P.convert(): MB = this@convert.toMB()
    override fun new(value: Double): MB = MB(value)
}

@JvmInline
@Serializable
public value class GB(override val value: Double): Data<GB> {
    override fun kBValue(): Double = value * 1024 * 1024
    override fun <P : Data<*>> P.convert(): GB = this@convert.toGB()
    override fun new(value: Double): GB = GB(value)
}
