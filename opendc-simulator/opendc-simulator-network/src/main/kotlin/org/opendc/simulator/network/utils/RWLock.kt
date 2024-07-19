package org.opendc.simulator.network.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore

internal class RWLock(private val readPermits: Int = 5) {

    private val sem = Semaphore(Int.MAX_VALUE)

    private var attemptingWMutex = Mutex()

    // lockRead and unlockRead not provided since it would allow
    // unlocking without locking, and preventing it would decrease performance
    // use withReadLock instead

    suspend fun lockWrite(owner: Any? = null) {
        attemptingWMutex.lock(owner)
        repeat(readPermits) { sem.acquire() }
        return
    }

    fun unlockWrite(owner: Any? = null) {
        attemptingWMutex.unlock(owner)
        repeat(readPermits) { sem.release() }
        return
    }

    suspend fun <T> withRLock(block: suspend () -> T): T {
        sem.acquire()
        val result: T = block()
        sem.release()
        return result
    }

    suspend fun <T> withWLock(block: suspend () -> T): T {
        lockWrite()
        val result: T = block()
        unlockWrite()
        return result
    }

    fun <T> tryWithRLock(block: () -> T): T? {
        return if (sem.tryAcquire()) {
            val result = block()
            sem.release()
            result
        } else null
    }
}
