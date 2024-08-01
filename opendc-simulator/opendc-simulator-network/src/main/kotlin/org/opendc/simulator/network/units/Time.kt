package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface Time<T>: Unit<T> where T : Unit<T>, T: Time<T> {
    public fun msValue(): Double
    public fun secValue(): Double = msValue() / 1000.0
    public fun hoursValue(): Double = secValue() / 60 / 60
    public fun toMs(): Ms = Ms(msValue())
    public fun toSec(): Seconds = Seconds(secValue())
    public fun toHours(): Hours = Hours(hoursValue())


    public fun <P: Time<*>> P.convert(): T

    public operator fun <P: Time<*>> plus(other: P): Time<T> =
        super.plus(other.convert())
    public operator fun <P: Time<*>> minus(other: P): Time<T> =
        super.minus(other.convert())
    public operator fun <P: Time<*>> compareTo(other: P): Int =
        super.compareTo(other.convert())
}

@JvmInline
@Serializable
public value class Ms(override val value: Double): Time<Ms> {
    public constructor(value: Long): this(value.toDouble())

    override fun msValue(): Double = value
    override fun toMs(): Ms = this
    override fun <P : Time<*>> P.convert(): Ms = this@convert.toMs()
    override fun new(value: Double): Ms = Ms(value)
    override fun toString(): String = "$value ms"
}

@JvmInline
@Serializable
public value class Seconds(override val value: Double): Time<Seconds> {
    override fun msValue(): Double = value * 1000.0
    override fun toSec(): Seconds = this
    override fun <P : Time<*>> P.convert(): Seconds = this@convert.toSec()
    override fun new(value: Double): Seconds = Seconds(value)
    override fun toString(): String = "$value s"
}


@JvmInline
@Serializable
public value class Hours(override val value: Double): Time<Hours> {
    override fun msValue(): Double = value * 60 * 60 * 1000
    override fun toHours(): Hours = this
    override fun <P : Time<*>> P.convert(): Hours = this@convert.toHours()
    override fun new(value: Double): Hours = Hours(value)
    override fun toString(): String = "$value h"
}
