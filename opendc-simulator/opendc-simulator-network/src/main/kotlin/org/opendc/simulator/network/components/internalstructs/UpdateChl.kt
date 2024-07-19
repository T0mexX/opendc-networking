package org.opendc.simulator.network.components.internalstructs

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.StabilityValidator.Invalidator
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.RWLock
import org.opendc.simulator.network.utils.logger


internal typealias RateUpdate = Map<FlowId, Kbps>

internal class UpdateChl private constructor(
    private val chl: Channel<RateUpdate>,
): Channel<RateUpdate> by chl {

    internal constructor()
        : this(chl = Channel<RateUpdate>(Channel.UNLIMITED))

    private val chlReceiveLock = RWLock()

    private var invalidator: Invalidator? = null

    var pending: Int = 1
    private val pendingLock = Mutex()


    internal suspend fun withInvalidator(inv: Invalidator): UpdateChl {
        invalidator = inv
        pendingLock.withLock { if (pending > 0) invalidator?.invalidate()}
        return this
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun tryReceive(): ChannelResult<RateUpdate> =
        chlReceiveLock.tryWithRLock {
            val chlResult = chl.tryReceive()
            chlResult.getOrNull()?.let {
                runBlocking { pendingLock.withLock { pending-- } }
            }

            chlResult
        } ?: ChannelResult.failure()


    override suspend fun receive(): RateUpdate =
        chlReceiveLock.withRLock {
            pendingLock.withLock {
                pending--
                if (pending == 0) invalidator?.validate()
            }

            chl.receive()
        }

    override suspend fun send(element: RateUpdate) {
        if (element.isEmpty()) return
        pendingLock.withLock {
            pending++
            if (pending == 1) invalidator?.invalidate()
        }

        chl.send(element)
    }

    suspend fun <T> whileReceivingLocked(block: suspend () -> T): T =
        chlReceiveLock.withRLock { block() }

    companion object {
        val log by logger()
    }
}
