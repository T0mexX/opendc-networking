package org.opendc.trace.table.column

import com.fasterxml.jackson.core.JsonParser
import kotlinx.serialization.json.Json
import org.opendc.trace.Trace
import org.opendc.trace.table.Table
import org.opendc.trace.table.TableReader
import org.opendc.trace.table.VirtualTable
import org.opendc.trace.util.logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration

/**
 * Class used to read values from a specific column of a [Table].
 * It is set up by user or preset [Trace]s on specific columns that
 * are needed to be read (sometime not all columns are needed).
 *
 * This class is needed to provide type safety to the user,
 * who will retrieve values of type [P], and at the same time allow
 * the [TableReader] to be generic (not tied to a specific trace)
 * and simply store as many unknown typed column readers as the user wants.
 * The user has direct access to the column readers.
 *
 * &nbsp;
 * ###### Examples:
 *
 *
 * ```kotlin
 * val tr: TableReader
 * val dataRateList: MutableList<Double /* Kbps */ > = emptyList()
 *
 * tr.withColumnReader(
 *      name = "data_rate",
 *      DoubleType, // KBps
 *      defaultValue = .0,
 *      process = { it * 8 /* KBps to Kbps */ },
 *      postProcess = { dataRateList.add(it) }
 * )
 *
 * tr.readAll()
 * println(dataRateList) // all column values added to the list in Kbps
 * ```
 *
 *
 * Simple use for preset traces
 * ```kotlin
 * val bitBrainsTrace: BitBrains
 * val tr: TableReader = bitBrainsTrace.getReader()
 * val idReader = tr.addColumnReader(BitBrains.VM_ID)
 * ...
 * tr.nextRow()
 * val id: Int = idReader.currVal
 * ```
 *
 *
 *
 * @param[O]                the row type decoded from the column entry.
 * @param[P]                the column entry after it has been processed by [process] (if O != P else O).
 * @property[name]          name of the column.
 * @property[columnType]    the column (raw [O]) type restricted to implementations of [ColumnReader.ColumnType].
 *                          Provides type specific parsing.
 * @property[defaultValue]  the value of the column if parsing fails (nullable so that the parameter
 *                          is optional and no other constructors are to be defined to provide null safety
 *                          at compile time, but null is not a valid default value and
 *                          a [RuntimeException] will be thrown in case the default value is needed)
 * @property[process]       lambda that processes the raw decoded type [O] into its
 *                          processed counterpart [P], then stored in [currRowValue].
 * @property[postProcess]   lambda called after the column entry has been processed, with the processed value as param.
 */
public open class ColumnReader<O, P: Any> internal constructor(
    public val name: String,
    private val columnType: ColumnType<O>,
    private val defaultValue: P? = null,
    private val process: (O) -> P,
    private val postProcess: ((P) -> Unit) = {},
) {

    public companion object {
        private val log by logger()

        /**
         * Used to decode values of type [O] from [String]s.
         */
        private val jsonParser = Json

        /**
         * Secondary constructor used when [O] == [P].
         * The double constructor allows to force a [process] parameter when [O] != [P].
         * It also allows the compiler to automatically infer O and P when no process function is provided,
         * hence no need for `ColumnReader<Long,Long>(...)` but just `ColumnReader(...)`.
         *
         * See primary constructor for parameters docs.
         */
        internal operator fun <T : Any> invoke(
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

    /**
     * The associated to this column in the current row.
     * [TableReader] provides stream-fashioned parsing (row by row).
     */
    public open lateinit var currRowValue: P
        protected set

    /**
     * Set by [TableReader] whenever the column reader is associated with a column
     * that is not present in the current table.
     *
     * Use cases:
     * - the current table is a concatenated table.
     * - the current column is an artificially added column.
     *
     * @see[Table.concatWithName]
     * @see[Table.withArtificialColumn]
     */
    internal fun setArtificially(value: Any) {
        @Suppress("UNCHECKED_CAST")
        currRowValue = value as P
        postProcess.invoke(currRowValue)
    }

    /**
     * Called by [TableReader] if to parse the current column value through a JsonParser.
     */
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

    /**
     * Called by [TableReader] if value is to be parsed from a string.
     *
     * Use cases:
     * - The current table is a virtual table, which has its values loaded into memory as strings.
     *
     * @see[VirtualTable]
     */
    internal fun setFromString(strValue: String) {
        currRowValue = process.invoke(columnType.fromStr(strValue))
    }

    /**
     * Provides a way to limit the raw type of the column readers at compile time.
     * Provides type specific parsing logic.
     * @param[T]    the row type associated with this column type (e.g. [ColumnReader.IntType] -> [Int])
     */
    public abstract class ColumnType<T> {
        internal abstract fun fromJsonParser(parser: JsonParser): T
        internal abstract fun fromStr(strValue: String): T
    }


    public object IntType: ColumnType<Int>() {
        override fun fromJsonParser(parser: JsonParser): Int = parser.intValue
        override fun fromStr(strValue: String): Int = jsonParser.decodeFromString(strValue)
    }

    public object DoubleType: ColumnType<Double>() {
        override fun fromJsonParser(parser: JsonParser): Double = parser.doubleValue
        override fun fromStr(strValue: String): Double = jsonParser.decodeFromString(strValue)
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

        override fun fromStr(strValue: String): Instant = jsonParser.decodeFromString(strValue)
    }

    public object LongType: ColumnType<Long>() {
        override fun fromJsonParser(parser: JsonParser): Long = parser.longValue
        override fun fromStr(strValue: String): Long = jsonParser.decodeFromString(strValue)
    }

    public object FloatType: ColumnType<Float>() {
        override fun fromJsonParser(parser: JsonParser): Float = parser.floatValue
        override fun fromStr(strValue: String): Float = jsonParser.decodeFromString(strValue)
    }

    public object BooleanType: ColumnType<Boolean>() {
        override fun fromJsonParser(parser: JsonParser): Boolean = parser.booleanValue
        override fun fromStr(strValue: String): Boolean = jsonParser.decodeFromString(strValue)
    }

    public object StringType: ColumnType<String>() {
        override fun fromJsonParser(parser: JsonParser): String = parser.valueAsString
        override fun fromStr(strValue: String): String = strValue
    }

    public object UUIDType: ColumnType<UUID>() {
        override fun fromJsonParser(parser: JsonParser): UUID = UUID.fromString(parser.valueAsString) // NEVER USED (every old TableReader::getUUID impl. throws exception)
        override fun fromStr(strValue: String): UUID = UUID.fromString(strValue)
    }

    public class DurationType(
        private val dfltValue: Duration? = null
    ): ColumnType<Duration>() {
        override fun fromJsonParser(parser: JsonParser): Duration =
            fromStr(parser.valueAsString)

        override fun fromStr(strValue: String): Duration =
            Duration.parseOrNull(strValue)
                ?: Duration.parseIsoStringOrNull(strValue)
                ?: dfltValue
                ?: throw RuntimeException("unable to parse duration in column entry")
    }

    public data class ListType<T>(private val colType: ColumnType<T>): ColumnType<List<T>>() {
        override fun fromJsonParser(parser: JsonParser): List<T> =
            parser.readValueAs(listOf<T>().javaClass)

        override fun fromStr(strValue: String): List<T> =
            jsonParser.decodeFromString(strValue)
    }

    public data class SetType<T>(private val colType: ColumnType<T>): ColumnType<Set<T>>() {
        override fun fromJsonParser(parser: JsonParser): Set<T> =
            parser.readValueAs(setOf<T>().javaClass)

        override fun fromStr(strValue: String): Set<T> =
            jsonParser.decodeFromString(strValue)
    }

    public data class MapType<T, R>(
        private val keyColType: ColumnType<T>,
        private val valueColType: ColumnType<R>
    ): ColumnType<Map<T, R>>() {
        override fun fromJsonParser(parser: JsonParser): Map<T, R> =
            parser.readValueAs(mapOf<T, R>().javaClass)

        override fun fromStr(strValue: String): Map<T, R> =
            jsonParser.decodeFromString(strValue)
    }
}
