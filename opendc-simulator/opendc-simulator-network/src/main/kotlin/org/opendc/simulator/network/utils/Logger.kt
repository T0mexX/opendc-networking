package org.opendc.simulator.network.util

import mu.KotlinLogging
import org.slf4j.Logger

/**
 * Returns a [Logger] with the class name of the caller, even if the caller is a companion object.
 */
internal fun <T: Any> T.logger(): Lazy<Logger> {
    return lazy { KotlinLogging.logger(this::class.java.enclosingClass.simpleName)}
}
