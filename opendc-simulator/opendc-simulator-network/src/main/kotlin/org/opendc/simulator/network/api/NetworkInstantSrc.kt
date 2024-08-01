package org.opendc.simulator.network.api

import org.opendc.simulator.network.api.NetworkController.Companion.log
import org.opendc.simulator.network.units.Ms
import org.opendc.simulator.network.utils.logger
import java.time.Instant
import java.time.InstantSource

internal class NetworkInstantSrc(
    private val external: InstantSource? = null,
    internal: Ms = Ms(Long.MIN_VALUE)
): InstantSource {
    var internal = internal
        private set
    val isExternalSource: Boolean = external != null
    val isInternalSource: Boolean = isExternalSource.not()
    override fun instant(): Instant =
        external?.instant()
            ?:  Instant.ofEpochMilli(internal.msValue().toLong())

    fun advanceTime(ms: Ms) {
        external?.let { return log.error("unable to advance internal time, network has external time source") }
        internal += ms
    }

    fun setInternalTime(ms: Ms) {
        internal = ms
    }
}
