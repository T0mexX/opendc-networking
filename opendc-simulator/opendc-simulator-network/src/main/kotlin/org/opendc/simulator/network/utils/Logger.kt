package org.opendc.simulator.network.utils

import mu.KotlinLogging
import org.slf4j.Logger

/**
 * Returns a [Logger] with the class name of the caller, even if the caller is a companion object.
 */
internal fun <T: Any> T.logger(name: String? = null): Lazy<Logger> {
    return lazy { KotlinLogging.logger(name ?: this::class.java.enclosingClass.simpleName)}
}
