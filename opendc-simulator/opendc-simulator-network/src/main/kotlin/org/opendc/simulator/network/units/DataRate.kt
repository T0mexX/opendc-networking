@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package org.opendc.simulator.network.units

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.simulator.network.utils.logger

@Serializable(with = BO::class)
public sealed interface DataRate<T>: Unit<DataRate<*>, T> where T: DataRate<T> {
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

    public companion object { public val ZERO: DataRate<*> = Kbps(.0) }
}

@JvmInline
@Serializable
public value class Kbps(override val value: Double): DataRate<Kbps> {
    override fun kbpsValue(): Double = value
    override fun DataRate<*>.convert(): Kbps = this@convert.toKbps()
    override fun new(value: Double): Kbps = Kbps(value)
    override fun toString(): String = "$value Kbps"
}

@JvmInline
@Serializable
public value class KBps(override val value: Double): DataRate<KBps> {
    override fun kbpsValue(): Double = value * 8
    override fun DataRate<*>.convert(): KBps = this@convert.toKBps()
    override fun new(value: Double): KBps = KBps(value)
    override fun toString(): String = "$value KBps"
}

@JvmInline
@Serializable
public value class Mbps(override val value: Double): DataRate<Mbps> {
    override fun kbpsValue(): Double = value * 1024
    override fun DataRate<*>.convert(): Mbps = this@convert.toMbps()
    override fun new(value: Double): Mbps = Mbps(value)
    override fun toString(): String = "$value Mbps"
}

@JvmInline
@Serializable
public value class MBps(override val value: Double): DataRate<MBps> {
    override fun kbpsValue(): Double = value * 8 * 1024
    override fun DataRate<*>.convert(): MBps = this@convert.toMBps()
    override fun new(value: Double): MBps = MBps(value)
    override fun toString(): String = "$value MBps"
}

@JvmInline
@Serializable
public value class Gbps(override val value: Double): DataRate<Gbps> {
    override fun kbpsValue(): Double = value * 1024 * 1024
    override fun DataRate<*>.convert(): Gbps = this@convert.toGbps()
    override fun new(value: Double): Gbps = Gbps(value)
    override fun toString(): String = "$value Gbps"
}

@JvmInline
@Serializable
public value class GBps(override val value: Double): DataRate<GBps> {
    override fun kbpsValue(): Double = value * 8 * 1024 * 1024
    override fun DataRate<*>.convert(): GBps = this@convert.toGBps()
    override fun new(value: Double): GBps = GBps(value)
    override fun toString(): String = "$value GBps"
}


internal class BO: KSerializer<DataRate<*>> {
    companion object { private val log by logger() }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("_", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DataRate<*> {
        TODO()
    }

    override fun serialize(encoder: Encoder, value: DataRate<*>) {
        throw IllegalStateException("A link list (List<Pair<NodeId, NodeId>>) should never be serialized.")
    }
}
