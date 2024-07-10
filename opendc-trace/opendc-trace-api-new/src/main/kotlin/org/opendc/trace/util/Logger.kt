package org.opendc.trace.util

import mu.KotlinLogging
import org.slf4j.Logger

/**
 * Returns a [Logger] with the class name of the caller, even if the caller is a companion object.
 */
internal fun <T: Any> T.logger(name: String? = null): Lazy<Logger> {
    return lazy { KotlinLogging.logger(name ?: this::class.java.enclosingClass.simpleName)}
}

internal fun Logger.errAndNull(msg: String): Nothing? {
    error(msg)
    return null
}

internal fun Logger.errAndFalse(msg: String): Boolean {
    error(msg)
    return false
}
