package org.opendc.simulator.network.components

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opendc.simulator.network.api.NOW

internal class StabilityValidator {

    private val stabilityLock = Mutex()

    private var invalidCount: Int = 0
        set(value) {
            println("${NOW()} inv count=$field -> $value")
            field = value
        }
    private val countLock = Mutex()

    internal suspend fun awaitStability() {
        stabilityLock.lock()
        stabilityLock.unlock()
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

