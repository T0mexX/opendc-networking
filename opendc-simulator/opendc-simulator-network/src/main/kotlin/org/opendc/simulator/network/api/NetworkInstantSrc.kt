package org.opendc.simulator.network.api

import org.opendc.simulator.network.api.NetworkController.Companion.log
import org.opendc.simulator.network.units.Time
import java.time.Instant
import java.time.InstantSource

internal class NetworkInstantSrc(
    private val external: InstantSource? = null,
    internal: Time = Time.ZERO
): InstantSource {
    var internal = internal
        private set
    val isExternalSource: Boolean = external != null
    val isInternalSource: Boolean = isExternalSource.not()
    override fun instant(): Instant =
        external?.instant()
            ?:  Instant.ofEpochMilli(internal.toMsLong())

    fun advanceTime(time: Time) {
        external?.let { return log.error("unable to advance internal time, network has external time source") }
        internal += time
    }

    fun setInternalTime(time: Time) {
        internal = time
    }
}
