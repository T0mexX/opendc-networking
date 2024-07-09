package org.opendc.trace.table

import com.fasterxml.jackson.core.JsonParser
import java.text.ParseException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration

@Suppress("UNCHECKED_CAST")
public class ColumnReader<O, P: Any> internal constructor(
    private val columnType: ColumnType<O>,
    private val process: (O) -> P = automaticConversion(),
    private val postProcess: ((P) -> Unit) = {},
) {
    internal companion object {
        fun <O, P> automaticConversion(): (O) -> P =
            { (it as? P) ?: throw RuntimeException("unable to process column value, " +
            "processing function not defined and automatic processing failed") }
    }

    public lateinit var currLineValue: P
        private set


    internal fun setFromJsonParser(parser: JsonParser) {
        currLineValue = process.invoke(columnType.fromJsonParser(parser))
        postProcess.invoke(currLineValue)
    }

    public abstract class ColumnType<T> {
        internal abstract fun fromJsonParser(parser: JsonParser): T
    }


    public object IntType: ColumnType<Int>() {
        override fun fromJsonParser(parser: JsonParser): Int = parser.intValue
    }

    public object DoubleType: ColumnType<Double>() {
        override fun fromJsonParser(parser: JsonParser): Double =
            parser.doubleValue
    }

    public object InstantType: ColumnType<Instant>() {
        override fun fromJsonParser(parser: JsonParser): Instant {
            return try {
                // used to parse the timestamps in case of the Materna trace?
                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

                LocalDateTime.parse(parser.text, formatter).toInstant(ZoneOffset.UTC)
            } catch (e: DateTimeParseException) {
                Instant.ofEpochSecond(parser.longValue)
            }
        }
    }

    public object LongType: ColumnType<Long>() {
        override fun fromJsonParser(parser: JsonParser): Long =
            parser.longValue
    }

    public object FloatType: ColumnType<Float>() {
        override fun fromJsonParser(parser: JsonParser): Float =
            parser.floatValue
    }

    public object BooleanType: ColumnType<Boolean>() {
        override fun fromJsonParser(parser: JsonParser): Boolean =
            parser.booleanValue
    }

    public object StringType: ColumnType<String>() {
        override fun fromJsonParser(parser: JsonParser): String =
            parser.valueAsString
    }

    public object UUIDType: ColumnType<UUID>() {
        override fun fromJsonParser(parser: JsonParser): UUID =
            UUID.fromString(parser.valueAsString) // NEVER USED (every old TableReader::getUUID impl. throws exception)
    }

    public class DurationType(
        private val dfltValue: Duration? = null
    ): ColumnType<Duration>() {
        override fun fromJsonParser(parser: JsonParser): Duration =
            Duration.parseOrNull(parser.valueAsString)
                ?: Duration.parseIsoStringOrNull(parser.valueAsString)
                ?: dfltValue
                ?: throw ParseException("unable to parse duration in column entry", parser.textOffset)
    }

    public data class ListType<T>(private val colType: ColumnType<T>): ColumnType<List<T>>() {
        override fun fromJsonParser(parser: JsonParser): List<T> =
            parser.readValueAs(listOf<T>().javaClass)
    }

    public data class SetType<T>(private val colType: ColumnType<T>): ColumnType<Set<T>>() {
        override fun fromJsonParser(parser: JsonParser): Set<T> =
            parser.readValueAs(setOf<T>().javaClass)
    }

    public data class MapType<T, R>(
        private val keyColType: ColumnType<T>,
        private val valueColType: ColumnType<R>
    ): ColumnType<Map<T, R>>() {
        override fun fromJsonParser(parser: JsonParser): Map<T, R> =
            parser.readValueAs(mapOf<T, R>().javaClass)
    }
}
