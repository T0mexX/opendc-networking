@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package org.opendc.simulator.network.units

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.whenMatch
import javax.naming.OperationNotSupportedException

@Serializable
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
@SerialName("Kbps")
public value class Kbps(override val value: Double): DataRate<Kbps> {
    override fun kbpsValue(): Double = value
    override fun DataRate<*>.convert(): Kbps = this@convert.toKbps()
    override fun new(value: Double): Kbps = Kbps(value)
    override fun toString(): String = "$value Kbps"
    private companion object { val regex = Regex("\\s*([\\d.e-])Kbps\\s*") }
}

@JvmInline
@Serializable
@SerialName("KBps")
public value class KBps(override val value: Double): DataRate<KBps> {
    override fun kbpsValue(): Double = value * 8
    override fun DataRate<*>.convert(): KBps = this@convert.toKBps()
    override fun new(value: Double): KBps = KBps(value)
    override fun toString(): String = "$value KBps"
}

@JvmInline
@Serializable
@SerialName("Mbps")
public value class Mbps(override val value: Double): DataRate<Mbps> {
    override fun kbpsValue(): Double = value * 1024
    override fun DataRate<*>.convert(): Mbps = this@convert.toMbps()
    override fun new(value: Double): Mbps = Mbps(value)
    override fun toString(): String = "$value Mbps"
}

@JvmInline
@Serializable
@SerialName("MBps")
public value class MBps(override val value: Double): DataRate<MBps> {
    override fun kbpsValue(): Double = value * 8 * 1024
    override fun DataRate<*>.convert(): MBps = this@convert.toMBps()
    override fun new(value: Double): MBps = MBps(value)
    override fun toString(): String = "$value MBps"
}

@JvmInline
@Serializable
@SerialName("Gbps")
public value class Gbps(override val value: Double): DataRate<Gbps> {
    override fun kbpsValue(): Double = value * 1024 * 1024
    override fun DataRate<*>.convert(): Gbps = this@convert.toGbps()
    override fun new(value: Double): Gbps = Gbps(value)
    override fun toString(): String = "$value Gbps"
}

@JvmInline
@Serializable(with = bo::class)
@SerialName("GBps")
public value class GBps(override val value: Double): DataRate<GBps> {
    override fun kbpsValue(): Double = value * 8 * 1024 * 1024
    override fun DataRate<*>.convert(): GBps = this@convert.toGBps()
    override fun new(value: Double): GBps = GBps(value)
    override fun toString(): String = "$value GBps"
}

private class Ser: KSerializer<Kbps> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("link-list", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Kbps {
        val bo = Json {  }
        val strField: String = decoder.decodeString()
        return try {
            Kbps(bo.decodeFromString(strField))
        } catch (_: Exception) {
            val no = Regex("")
            whenMatch(strField) {
                no.invoke{ return@dese Kbps(.0) }
            }
            throw RuntimeException()
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        TODO("Not yet implemented")
    }

}


internal class bo2<T: DataRate<T>>: KSerializer<T> {
    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun deserialize(decoder: Decoder): T {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: T) {
        TODO("Not yet implemented")
    }

}

internal class bo: KSerializer<DataRate<*>> {
    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun deserialize(decoder: Decoder): DataRate<*> {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: DataRate<*>) {
        TODO("Not yet implemented")
    }

}
