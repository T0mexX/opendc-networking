package org.opendc.simulator.network.utils


internal class PatternMatching<T>(private val value: String) {
    private var handled = false
    private var _result: T? = null
    internal val result: T get() = _result ?: throw RuntimeException("no match")

    operator fun Regex.invoke(block: MatchResult.() -> T) {
        if (handled) return
        _result = matchEntire(value)?.block()?.also { handled = true }
    }

    fun otherwise(block: () -> T) {
        if (handled) return
        _result = block()
    }
}


internal fun <T> whenMatch(
    value: String,
    block: PatternMatching<T>.() -> Unit
): T {
    val pm = PatternMatching<T>(value)
    pm.block()
    return pm.result
}
