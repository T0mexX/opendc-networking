package org.opendc.simulator.network.utils

internal class KeepTrying<T> {
    private var lastExc: Exception? = null
    private var result: T? = null
    fun tryThis(block: () -> T): KeepTrying<T> {
        try {
            result = block();
            lastExc = null
        } catch (e: Exception) { lastExc = e }

        return this
    }

    fun elseThis(block: () -> T): KeepTrying<T> {
        result
            ?: try {
                result = block()
                lastExc = null
            } catch (e: Exception) { lastExc = e }

        return this
    }

    fun ifError(block: (Exception) -> kotlin.Unit): KeepTrying<T> {
        lastExc?.let { block(it) }

        return this
    }

    fun getOrNull(): T? = result

    fun get(): T = result!!
}

internal fun <T> tryThis(block: () -> T): KeepTrying<T> =
    KeepTrying<T>().elseThis(block)
