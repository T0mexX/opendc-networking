package org.opendc.simulator.network.api

import org.opendc.simulator.network.api.NetworkController.Companion.log
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import java.time.Instant
import java.time.InstantSource

internal class NetworkInstantSrc(
    private val external: InstantSource?,
    var internal: ms = ms.MIN_VALUE
): InstantSource {
    val isExternalSource: Boolean = external != null
    override fun instant(): Instant =
        external?.instant()
            ?:  Instant.ofEpochMilli(internal)

    fun advanceTime(ms: ms) {
        external?.let { return log.error("unable to advance internal time, network has external time source") }
        internal += ms
    }
}
