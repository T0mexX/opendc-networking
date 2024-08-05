package org.opendc.simulator.network.components

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks the network stability. Each instance of [Invalidator]
 * can invalidate its own state and consequently the
 * state of the network (if undergoing some update, connection, etc.).
 *
 * One can wait for the network to be stable with [awaitStability].
 */
internal class StabilityValidator {

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

    private var shouldBeStable: Boolean = false
    private val shouldBeStableLock: Mutex = Mutex()

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

    /**
     * @throws IllegalStateException
     */
    internal suspend fun <T> shouldBeStableWhile(block: () -> T) =
        shouldBeStableLock.withLock {
            shouldBeStable = true
            val res = block()
            shouldBeStable = false

            res
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
        internal var isValid = true

        /**
         * Invalidates the state of this instance if not already invalidated.
         * The network state will not be valid until all invalidators validate their own state.
         */
        suspend fun invalidate() {
            if (isValid.not()) return
            check(shouldBeStable.not())

            countLock.withLock {
                invalidCount++
                if (invalidCount == 1)
                    stabilityLock.lock(this@StabilityValidator)
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
                    stabilityLock.unlock(this@StabilityValidator)
            }
            isValid = isValid.not()
        }
    }
}

