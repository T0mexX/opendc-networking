package org.opendc.simulator.network.units

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import org.opendc.simulator.network.utils.InternalUse
import org.opendc.simulator.network.utils.tryThis
import org.opendc.simulator.network.utils.whenMatch

@Serializable
@JvmInline public value class DataRate private constructor(override val value: Double): Unit<DataRate> {
    @InternalUse
    override fun new(value: Double): DataRate = DataRate(value)


    public fun toKbps(): Double = value
    public fun toKBps(): Double = value / 8
    public fun toMbps(): Double = value / 1024
    public fun toMBps(): Double = value / 1024 / 8
    public fun toGbps(): Double = value / 1024 / 1024
    public fun toGBps(): Double = value / 1024 / 1024 / 8


    override fun toString(): String {
        val `100Kbps` = 100.0
        val `100Mbps` = 1024.0 * 100.0

        return when (value) {
            in (Double.MIN_VALUE..`100Kbps`) -> "${toKbps()} Kbps"
            in (`100Kbps`.. `100Mbps`) -> "${toMbps()} Kbps"
            else -> "${toGbps()} Gbps"
        }
    }

    public companion object {
        public val ZERO: DataRate = DataRate(.0)
        public fun ofKbps(kbps: Double): DataRate = DataRate(kbps)
        public fun ofKBps(kBps: Double): DataRate = DataRate(kBps * 8)
        public fun ofMbps(mbps: Double): DataRate = DataRate(mbps * 1024)
        public fun ofMBps(mBps: Double): DataRate = DataRate(mBps * 1024 * 8)
        public fun ofGbps(gbps: Double): DataRate = DataRate(gbps * 1024 * 1024)
        public fun ofGBps(gBps: Double): DataRate = DataRate(gBps * 8 * 1024 * 1024)
    }
}

private object DataRateSerializer: OnlyString<DataRate>(
    object: KSerializer<DataRate> {
        val kbpsReg = Regex("\\s*([\\de.-]+)\\s*(?:Kbps|kbps)\\s*")
        val kBpsReg = Regex("\\s*([\\de.-]+)\\s*(?:KBps|kBps)\\s*")
        val mbpsReg = Regex("\\s*([\\de.-]+)\\s*(?:Mbps|mbps)\\s*")
        val mBpsReg = Regex("\\s*([\\de.-]+)\\s*(?:MBps|mBps)\\s*")
        val gbpsReg = Regex("\\s*([\\de.-]+)\\s*(?:Gbps|gbps)\\s*")
        val gBpsReg = Regex("\\s*([\\de.-]+)\\s*(?:GBps|gBps)\\s*")

        override val descriptor: SerialDescriptor = TODO()
        override fun deserialize(decoder: Decoder): DataRate {
            val json = Json
            val strField = decoder.decodeString()
            return tryThis {
                DataRate.ofKbps(json.decodeFromString(strField))
            }.elseThis {
                whenMatch(strField) {
                    kbpsReg { DataRate.ofKbps(json.decodeFromString(groupValues[1])) }
                    kBpsReg { DataRate.ofKBps(json.decodeFromString(groupValues[1])) }
                    mbpsReg { DataRate.ofMbps(json.decodeFromString(groupValues[1])) }
                    mBpsReg { DataRate.ofMBps(json.decodeFromString(groupValues[1])) }
                    gbpsReg { DataRate.ofGbps(json.decodeFromString(groupValues[1])) }
                    gBpsReg { DataRate.ofGBps(json.decodeFromString(groupValues[1])) }
                }
            }.ifError {
                println(it.message)
            }.getOrNull()
                ?: throw RuntimeException("unable to parse data rate '$strField'")
        }
        override fun serialize(encoder: Encoder, value: DataRate) {
            encoder.encodeString(value.toString())
        }
    }
)

internal open class OnlyString<T: Any>(tSerial: KSerializer<T>): JsonTransformingSerializer<T>(tSerial) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonPrimitive(element.toString().trim('"'))
}

