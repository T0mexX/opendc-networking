package org.opendc.simulator.network.utils

import mu.KLogger
import mu.KotlinLogging
import org.opendc.simulator.network.utils.Result
import org.slf4j.Logger
import kotlin.system.measureNanoTime

/**
 * Returns a [Logger] with the class name of the caller, even if the caller is a companion object.
 */
internal fun <T: Any> T.logger(name: String? = null): Lazy<Logger> {


    return lazy {
        KotlinLogging.logger(
            name
                ?: runCatching { this::class.java.enclosingClass.simpleName }
                    .getOrNull()
                ?: "unknown"
        )
    }
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

internal fun <T> Logger.withWarn(obj: T, msg: String): T {
    this.warn(msg)
    return obj
}

internal fun <T> Logger.withErr(obj: T, msg: String): T {
    this.error(msg)
    return obj
}


//internal abstract class WarnOnceLogger {
//    protected abstract val log: Logger
//
//    private val warned = mutableSetOf<Int>()
//
//    internal fun warnOnce(warnId: Int, msg: String) {
//        measureNanoTime {
//            if (warnId !in warned) {
//                warned.add(warnId)
//                log.warn(msg)
//            }
//        }
//    }
//
//    internal fun <T> withWarnOnce(obj: T, warnId: Int, msg: String): T {
//        warnOnce(warnId, msg)
//        return obj
//    }
//}

internal fun Logger.warnOnce(warnOnce: WarnOnce) {
    if (warnOnce.toWarn) this.warn(warnOnce.msg)
}

internal class WarnOnce(val msg: String, var toWarn: Boolean = true)




