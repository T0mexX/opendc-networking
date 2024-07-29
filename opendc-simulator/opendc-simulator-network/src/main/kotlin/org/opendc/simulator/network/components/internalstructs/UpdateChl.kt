package org.opendc.simulator.network.components.internalstructs

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.components.StabilityValidator.Invalidator
import org.opendc.simulator.network.flow.RateUpdt
import org.opendc.simulator.network.utils.RWLock
import org.opendc.simulator.network.utils.logger


internal class UpdateChl private constructor(
    private val chl: Channel<RateUpdt>,
): Channel<RateUpdt> by chl {

    internal constructor()
        : this(chl = Channel<RateUpdt>(Channel.UNLIMITED))

    private val chlReceiveLock = RWLock()

    private var invalidator: Invalidator? = null

    var pending: Int = 1
    private val pendingLock = Mutex()


    fun clear() {
        do { val res = chl.tryReceive() }
        while (res.getOrNull() != null)
    }

    suspend fun withInvalidator(inv: Invalidator): UpdateChl {
        invalidator = inv
        pendingLock.withLock { if (pending > 0) inv.invalidate() else inv.validate()}
        return this
    }


    @OptIn(InternalCoroutinesApi::class)
    suspend fun tryReceiveSus(): ChannelResult<RateUpdt> =
        chlReceiveLock.withRLock {
            val chlResult = chl.tryReceive()
            chlResult.getOrNull()?.let {
                pendingLock.withLock {
                    pending--
                    check (pending > 0)
                }
            }

            chlResult
        } ?: ChannelResult.failure()


    @Deprecated(level = DeprecationLevel.ERROR, message = "use suspending version instead",
        replaceWith = ReplaceWith("tryReceiveSus()")
    )
    override fun tryReceive(): ChannelResult<RateUpdt> =
        throw UnsupportedOperationException()

    override suspend fun receive(): RateUpdt =
        chlReceiveLock.withRLock {
            pendingLock.withLock {
                pending--
                if (pending == 0) invalidator?.validate()
            }

            chl.receive()
        }

    override suspend fun send(element: RateUpdt) {
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