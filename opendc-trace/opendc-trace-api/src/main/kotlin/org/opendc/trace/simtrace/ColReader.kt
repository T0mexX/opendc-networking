package org.opendc.trace.simtrace

import org.opendc.trace.TableReader
import java.time.Duration
import java.time.Instant
import java.util.UUID

public class ColReader<C, P>(
    private val colName: String,
    private val type: ColType<C>,
    private val process: ((C) -> P) = { throw RuntimeException("process function not set") }
    ) {

    public fun processed(tr: TableReader): P =
        process.invoke(read(tr))

    private var idx: Int? = null

    public fun read(tr: TableReader, retry: Boolean = true): C {
        return try {
            type.read(tr, idx!!)
        } catch (e: Exception) {
            if (retry) {
                resolveColIdx(tr)
                read(tr, retry = false)
            } else throw e
        }
    }

    private fun resolveColIdx(tr: TableReader) {
        idx = tr.resolve(colName)
    }

    public abstract class ColType<T> {
        internal abstract val read: TableReader.(Int) -> T
        internal abstract fun getClass(): Class<T>
    }

    public object IntType: ColType<Int>() {
        override val read: TableReader.(Int) -> Int = TableReader::getInt
        override fun getClass(): Class<Int> = Int::class.java
    }

    public object DoubleType: ColType<Double>() {
        override val read: TableReader.(Int) -> Double = TableReader::getDouble
        override fun getClass(): Class<Double> = Double::class.java
    }

    public object InstantType: ColType<Instant>() {
        override val read: TableReader.(Int) -> Instant = { this.getInstant(it)!! }
        override fun getClass(): Class<Instant> = Instant::class.java
    }

    public object LongType: ColType<Long>() {
        override val read: TableReader.(Int) -> Long = TableReader::getLong
        override fun getClass(): Class<Long> = Long::class.java
    }

    public object FloatType: ColType<Float>() {
        override val read: TableReader.(Int) -> Float = TableReader::getFloat
        override fun getClass(): Class<Float> = Float::class.java
    }

    public object BooleanType: ColType<Boolean>() {
        override val read: TableReader.(Int) -> Boolean = TableReader::getBoolean
        override fun getClass(): Class<Boolean> = Boolean::class.java
    }

    public object StringType: ColType<String>() {
        override val read: TableReader.(Int) -> String = { this.getString(it)!! }
        override fun getClass(): Class<String> = String::class.java
    }

    public object UUIDType: ColType<UUID>() {
        override val read: TableReader.(Int) -> UUID = { this.getUUID(it)!! }
        override fun getClass(): Class<UUID> = UUID::class.java
    }

    public object DurationType: ColType<Duration>() {
        override val read: TableReader.(Int) -> Duration = { this.getDuration(it)!! }
        override fun getClass(): Class<Duration> = Duration::class.java
    }

    public data class ListType<T>(private val colType: ColType<T>): ColType<List<T>>() {
        override val read: TableReader.(Int) -> List<T> = { this.getList(it, colType.getClass())!! }
        override fun getClass(): Class<List<T>> = listOf<T>().javaClass
    }

    public data class SetType<T>(private val colType: ColType<T>): ColType<Set<T>>() {
        override val read: TableReader.(Int) -> Set<T> = { this.getSet(it, colType.getClass())!! }
        override fun getClass(): Class<Set<T>> = setOf<T>().javaClass
    }

    public data class MapType<T, R>(
        private val keyColType: ColType<T>,
        private val valueColType: ColType<R>
    ): ColType<Map<T, R>>() {
        override val read: TableReader.(Int) -> Map<T, R> = {
            this.getMap(it, keyColType.getClass(), valueColType.getClass())!!
        }
        override fun getClass(): Class<Map<T, R>> = mapOf<T, R>().javaClass
    }

}