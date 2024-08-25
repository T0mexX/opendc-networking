package org.opendc.simulator.network.playground

import org.opendc.common.units.Time
import java.time.Instant

internal class PGTimeSource(private val initialInstant: Instant) {
    internal val currentInstant: Instant get() =
        (Time.ofInstantFromEpoch(initialInstant) + _timeElapsed).toInstantFromEpoch()

    internal val timeElapsed: Time get() = _timeElapsed
    private var _timeElapsed: Time = Time.ZERO

    internal fun advanceBy(timeDelta: Time) {
        _timeElapsed += timeDelta
    }
}
