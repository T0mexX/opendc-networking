package org.opendc.simulator.network.utils

import mu.KotlinLogging
import org.opendc.simulator.network.utils.Result
import org.slf4j.Logger

/**
 * Returns a [Logger] with the class name of the caller, even if the caller is a companion object.
 */
internal fun <T: Any> T.logger(name: String? = null): Lazy<Logger> {
    return lazy { KotlinLogging.logger(name ?: this::class.java.enclosingClass.simpleName)}
}

/**
 * Logs [msg] as error and returns an instance of [Result.ERROR].
 * @param[msg]      msg of the error to be logged.
 * @return          an instance of [Result.ERROR] with [msg].
 */
internal fun Logger.errAndGet(msg: String): Result.ERROR {
    error(msg)
    return Result.ERROR(msg)
}
