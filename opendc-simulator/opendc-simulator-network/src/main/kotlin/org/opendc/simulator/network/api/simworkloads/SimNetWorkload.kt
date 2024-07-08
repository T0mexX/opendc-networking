package org.opendc.simulator.network.api.simworkloads

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import java.io.File
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

public class SimNetWorkload internal constructor(netEvents: List<NetworkEvent>) {
    private companion object { private val log by logger() }


    private val events: Queue<NetworkEvent> = LinkedList(netEvents.sorted())

    public val startInstant: Instant = events.peek()?.deadline?.let { Instant.ofEpochMilli(it) }
        ?: Instant.ofEpochMilli(ms.MIN_VALUE)

    internal fun execAll(controller: NetworkController) {
        execUntil(controller, ms.MAX_VALUE)
    }

    internal fun execNext(controller: NetworkController) {
        events.poll()?.execIfNotPassed(controller)
            ?: log.error("unable to execute network event, no more events remaining in the workload")
    }

    internal fun execUntil(controller: NetworkController, until: Instant) {
        execUntil(controller, until.toEpochMilli())
    }

    internal fun execUntil(controller: NetworkController, until: ms) {
        while ((events.peek()?.deadline ?: ms.MAX_VALUE) < until) {
            events.poll()?.execIfNotPassed(controller)
        }
    }

    public fun execOn(
        controller: NetworkController,
        resetFlows: Boolean = true,
        resetTime: Boolean = true,
        resetEnergy: Boolean = true
    ) {
        controller.execWorkload(
            netWorkload = this,
            resetFlows = resetFlows,
            resetTime = resetTime,
            resetEnergy = resetEnergy
        )
    }

    public fun execOn(networkFile: File) {
        NetworkController.fromFile(networkFile).execWorkload(this)
    }
}
