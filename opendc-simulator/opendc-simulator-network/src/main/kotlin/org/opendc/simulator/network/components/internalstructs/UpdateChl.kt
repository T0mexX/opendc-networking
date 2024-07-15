package org.opendc.simulator.network.components.internalstructs

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.api.NetworkStabilizer.Invalidator
import org.opendc.simulator.network.components.link.RateUpdate
import org.opendc.simulator.network.utils.logger

internal class UpdateChl private constructor(
    private val chl: Channel<RateUpdate>,
): Channel<RateUpdate> by chl {

    internal constructor()
        : this(chl = Channel<RateUpdate>(Channel.UNLIMITED))

    private var invalidator: Invalidator? = null

    var pending: Int = 1
    private val pendingLock = Mutex()

    internal fun withInvalidator(inv: Invalidator): UpdateChl = runBlocking {
        invalidator = inv
        pendingLock.withLock { if (pending > 0) invalidator?.invalidate()}
        this@UpdateChl
    }

    override fun tryReceive(): ChannelResult<RateUpdate> {
        val chlResult = chl.tryReceive()
        chlResult.getOrNull()?.let {
            runBlocking { pendingLock.withLock { pending-- } }
        }

        return chlResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun receive(): RateUpdate {
        pendingLock.withLock {
            pending--
            if (pending == 0) invalidator?.validate()
        }

        val tmp = chl.receive()
        return tmp
    }

    override suspend fun send(element: RateUpdate) {
        pendingLock.withLock {
            pending++
            if (pending == 1) invalidator?.invalidate()
        }

        chl.send(element)
    }

    companion object {
        val log by logger()
    }
}
