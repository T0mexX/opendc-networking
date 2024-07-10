package org.opendc.trace.table.column

import com.fasterxml.jackson.core.JsonParser
import org.opendc.trace.util.logger
import java.text.ParseException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration

@Suppress("UNCHECKED_CAST")
public open class ColumnReader<O, P: Any> internal constructor(
    public val name: String,
    private val columnType: ColumnType<O>,
    private val defaultValue: P? = null,
    private val process: (O) -> P,
    private val postProcess: ((P) -> Unit) = {},
) {


    public companion object {
        private val log by logger()

        // constructor for non-process column (a column whose read value type is the one returned)
        public operator fun <T: Any> invoke(
            name: String,
            columnType: ColumnType<T>,
            defaultValue: T? = null,
            postProcess: (T) -> Unit
        ): ColumnReader<T, T> =
            ColumnReader(
                name = name,
                columnType = columnType,
                defaultValue = defaultValue,
                process = { it },
                postProcess = postProcess
            )
    }

    public open lateinit var currRowValue: P
        protected set

    internal fun setArtificially(value: Any) {
        currRowValue = value as P
        postProcess.invoke(currRowValue)
    }

    internal fun setFromJsonParser(parser: JsonParser) {
        try {
            currRowValue = process.invoke(columnType.fromJsonParser(parser))
        } catch (e: Exception) {
            try {
                defaultValue!!
            } catch (_: Exception) {
                log.error("unable to parse table value '${parser.valueAsString}, and no default value provided (null is not valid)")
                throw e
            }
        } finally {
            postProcess.invoke(currRowValue)
        }
    }

    public abstract class ColumnType<T> {
        internal abstract fun fromJsonParser(parser: JsonParser): T
    }


    public object IntType: ColumnType<Int>() {
        override fun fromJsonParser(parser: JsonParser): Int =
            parser.intValue
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
