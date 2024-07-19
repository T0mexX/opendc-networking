package org.opendc.simulator.network.utils


internal class PatternMatching(private val value: String) {
    private var handled = false

    operator fun Regex.invoke(block: MatchResult.() -> Unit) {
        if (handled) return
        matchEntire(value)?.block()?.also { handled = true }
    }

    fun otherwise(block: () -> Unit) {
        if (handled) return
        block()
    }
}


internal fun whenMatch(
    value: String,
    block: PatternMatching.() -> Unit
) = PatternMatching(value).block()
