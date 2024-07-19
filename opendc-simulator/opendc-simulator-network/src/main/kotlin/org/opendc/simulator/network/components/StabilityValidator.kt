package org.opendc.simulator.network.components

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class StabilityValidator {

    private var stabilityLock = Mutex()

    private var invalidCount: Int = 0
    private var countLock = Mutex()

    internal suspend fun awaitStability() {
        stabilityLock.lock()
        stabilityLock.unlock()
    }

    internal fun reset() {
        invalidCount = 0
        countLock = Mutex()
        stabilityLock = Mutex()
    }


    // assumed to be used synchronously
    internal inner class Invalidator {
        private var isValid = true

        suspend fun invalidate() {
            if (isValid.not()) return
            countLock.withLock {
                invalidCount++
                if (invalidCount == 1)
                    stabilityLock.lock(this@StabilityValidator)
            }
            isValid = isValid.not()
        }

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

