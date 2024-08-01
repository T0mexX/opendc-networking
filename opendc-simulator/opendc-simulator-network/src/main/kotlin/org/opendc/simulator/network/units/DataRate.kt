package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
public sealed interface DataRate<T>: Unit<T> where T : Unit<T>, T: DataRate<T> {
    public fun kbpsValue(): Double
    public fun kBpsValue(): Double = kbpsValue() / 8
    public fun mbpsValue(): Double = kbpsValue() / 1024
    public fun mBpsValue(): Double = mBpsValue() / 8
    public fun gbpsValue(): Double = mBpsValue() / 1024
    public fun gBpsValue(): Double = gbpsValue() / 8

    public fun toKbps(): Kbps = Kbps(kbpsValue())
    public fun toKBps(): KBps = KBps(kBpsValue())
    public fun toMbps(): Mbps = Mbps(mbpsValue())
    public fun toMBps(): MBps = MBps(mBpsValue())
    public fun toGbps(): Gbps = Gbps(gbpsValue())
    public fun toGBps(): GBps = GBps(gBpsValue())


    public fun <P: DataRate<*>> P.convert(): T

    public operator fun <P: DataRate<*>> plus(other: P): DataRate<T> = super.plus(other.convert())
    public operator fun <P: DataRate<*>> minus(other: P): DataRate<T> = super.minus(other.convert())
    public operator fun <P: DataRate<*>> compareTo(other: P): Int = super.compareTo(other.convert())
}

@JvmInline
@Serializable
public value class Kbps(override val value: Double): DataRate<Kbps> {
    override fun kbpsValue(): Double = value
    override fun <P : DataRate<*>> P.convert(): Kbps = this@convert.toKbps()
    override fun new(value: Double): Kbps = Kbps(value)
}

@JvmInline
@Serializable
public value class KBps(override val value: Double): DataRate<KBps> {
    override fun kbpsValue(): Double = value * 8
    override fun <P : DataRate<*>> P.convert(): KBps = this@convert.toKBps()
    override fun new(value: Double): KBps = KBps(value)
}

@JvmInline
@Serializable
public value class Mbps(override val value: Double): DataRate<Mbps> {
    override fun kbpsValue(): Double = value * 1024
    override fun <P : DataRate<*>> P.convert(): Mbps = this@convert.toMbps()
    override fun new(value: Double): Mbps = Mbps(value)
}

@JvmInline
@Serializable
public value class MBps(override val value: Double): DataRate<MBps> {
    override fun kbpsValue(): Double = value * 8 * 1024
    override fun <P : DataRate<*>> P.convert(): MBps = this@convert.toMBps()
    override fun new(value: Double): MBps = MBps(value)
}

@JvmInline
@Serializable
public value class Gbps(override val value: Double): DataRate<Gbps> {
    override fun kbpsValue(): Double = value * 1024 * 1024
    override fun <P : DataRate<*>> P.convert(): Gbps = this@convert.toGbps()
    override fun new(value: Double): Gbps = Gbps(value)
}

@JvmInline
@Serializable
public value class GBps(override val value: Double): DataRate<GBps> {
    override fun kbpsValue(): Double = value * 8 * 1024 * 1024
    override fun <P : DataRate<*>> P.convert(): GBps = this@convert.toGBps()
    override fun new(value: Double): GBps = GBps(value)
}
