package org.opendc.simulator.network.units

import kotlinx.serialization.Serializable

@Serializable
internal sealed interface DataRate<T: Unit<T>>: Unit<T> {
    fun kbpsValue(): Double
    fun kBpsValue(): Double = kbpsValue() / 8
    fun mbpsValue(): Double = kbpsValue() / 1024
    fun mBpsValue(): Double = mBpsValue() / 8
    fun gbpsValue(): Double = mBpsValue() / 1024
    fun gBpsValue(): Double = gbpsValue() / 8

    fun toKbps(): Kbps = Kbps(kbpsValue())
    fun toKBps(): KBps = KBps(kBpsValue())
    fun toMbps(): Mbps = Mbps(mbpsValue())
    fun toMBps(): MBps = MBps(mBpsValue())
    fun toGbps(): Gbps = Gbps(gbpsValue())
    fun toGBps(): GBps = GBps(gBpsValue())
}

@JvmInline
@Serializable
internal value class Kbps(override val value: Double): DataRate<Kbps> {
    override fun kbpsValue(): Double = value
    override fun new(value: Double): Kbps = Kbps(value)
}

@JvmInline
@Serializable
internal value class KBps(override val value: Double): DataRate<KBps> {
    override fun kbpsValue(): Double = value * 8
    override fun new(value: Double): KBps = KBps(value)
}

@JvmInline
@Serializable
internal value class Mbps(override val value: Double): DataRate<Mbps> {
    override fun kbpsValue(): Double = value * 1024
    override fun new(value: Double): Mbps = Mbps(value)
}

@JvmInline
@Serializable
internal value class MBps(override val value: Double): DataRate<MBps> {
    override fun kbpsValue(): Double = value * 8 * 1024
    override fun new(value: Double): MBps = MBps(value)
}

@JvmInline
@Serializable
internal value class Gbps(override val value: Double): DataRate<Gbps> {
    override fun kbpsValue(): Double = value * 1024 * 1024
    override fun new(value: Double): Gbps = Gbps(value)
}

@JvmInline
@Serializable
internal value class GBps(override val value: Double): DataRate<GBps> {
    override fun kbpsValue(): Double = value * 8 * 1024 * 1024
    override fun new(value: Double): GBps = GBps(value)
}
