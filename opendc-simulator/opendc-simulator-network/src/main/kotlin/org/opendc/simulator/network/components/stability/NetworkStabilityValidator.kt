package org.opendc.simulator.network.components.stability

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Tracks the network stability. Each instance of [Invalidator]
 * can invalidate its own state and consequently the
 * state of the network (if undergoing some update, connection, etc.).
 *
 * If the network is stable, then all its nodes are
 * suspended waiting for data rate updates.
 *
 * One can wait for the network to be stable with [awaitStability].
 *
 * One can check that a certain block of code is executed while
 * the network is stable with [checkIsStableWhile]
 */
internal class NetworkStabilityValidator: NetworkStabilityChecker() {

    /**
     * Locked when the network is not stable.
     */
    private var stabilityLock = Mutex()

    /**
     * Number of [Invalidator]s that have currently invalidated the network.
     * When this field is 0 the network is stable and [stabilityLock] is unlocked.
     */
    private var invalidCount: Int = 0
    private var countLock = Mutex()

    /**
     * This property is used to check at runtime that a
     * certain block of code is executed while the network is stable,
     * using [checkIsStableWhile].
     *
     * While this property is `true`, any attempt to invalidate the network
     * stability throws an [IllegalStateException]
     *
     * @see[checkIsStableWhile]
     */
    private var shouldBeStable: Boolean = false

    /**
     * Number of block that are currently being executed in the [checkIsStableWhile] method.
     */
    private var shouldBeStableCounter: Int = 0
    private val shouldBeStableLock: Mutex = Mutex()

    private suspend fun shouldBeStableCounterInc() =
        shouldBeStableLock.withLock {
            shouldBeStableCounter++
            if (shouldBeStableCounter == 1) shouldBeStable = false
        }
    private suspend fun shouldBeStableCounterDec() =
        shouldBeStableLock.withLock {
            shouldBeStableCounter--
            if (shouldBeStableCounter == 0) shouldBeStable = false
        }

    /**
     * Suspend method that suspends until the network reached a stable state.
     */
    internal suspend fun awaitStability() {
        stabilityLock.lock()
        stabilityLock.unlock()
    }

    /**
     * Resets [invalidCount]. Old [Invalidator]s can still invalidate the state of the network.
     */
    internal fun reset() {
        invalidCount = 0
        countLock = Mutex()
        stabilityLock = Mutex()
    }

    override suspend fun <T> checkIsStableWhile(block: suspend () -> T): T {
        // If network not stable throw error
        check(stabilityLock.tryLock().not())
        {"block of code that needs to be executed while network is stable was invoked while network unstable"}

        // While counter is non-zero if network is invalidated, then exception is thrown.
        shouldBeStableCounterInc()
        stabilityLock.unlock()

        val res = block()
        shouldBeStableCounterDec()

        return res
    }

    override fun isStable(): Boolean =
        stabilityLock.tryLock().let {
            if (it.not()) return@let false
            stabilityLock.unlock()
            true
        }

    /**
     * Instances of this class can invalidate their state,
     * and consequently, the state of the network.
     */
    internal inner class Invalidator {
        /**
         * Keeps track of the last state of this invalidator.
         * [invalidate] and [validate] are assumed to be used synchronously.
         */
        private var isValid = true

        /**
         * Invalidates the state of this instance if not already invalidated.
         * The network state will not be valid until all invalidators validate their own state.
         */
        suspend fun invalidate() {
            if (isValid.not()) return

            // If a block is being executed in the [checkIsStableWhile] function then throws exception
            check(shouldBeStable.not())
            {"attempted to invalidate network stability while a stability required block is being executed"}

            countLock.withLock {
                invalidCount++
                if (invalidCount == 1)
                    stabilityLock.lock(this@NetworkStabilityValidator)
            }
            isValid = isValid.not()
        }

        /**
         * Validates the state of this instance if not already validated.
         * The network will not be valid until all validators validate their own state.
         */
        suspend fun validate() {
            if (isValid) return
            countLock.withLock {
                invalidCount--
                if (invalidCount == 0)
                    stabilityLock.unlock(this@NetworkStabilityValidator)
            }
            isValid = isValid.not()
        }
    }
}

