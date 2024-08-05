package org.opendc.simulator.network.components.stability

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that allows to check whether a specific block
 * of code is executed in its entirety while the network is stable, else throwing exception.
 * The interface provided by this class does not allow to instantiate invalidators or reset the validator stat
 *
 * Useful to validate code.
 */
internal abstract class NetworkStabilityChecker: AbstractCoroutineContextElement(Key) {

    /**
     * Checks that the network remains stable while [block] is executed.
     * If network is invalidated while block is being executed an
     * [IllegalStateException] is thrown.
     *
     * Useful to check consistency. **This function should never be
     * invoked in any node runner coroutine**, since if it is invoked it means that the node is not
     * stable (not suspended in receiving update) and an exception will be thrown. It is useful for methods
     * that need to access properties that are modified during the node runner main loop from another coroutine.
     *
     * It is the caller responsibility to use the correct stability
     * checker (associated to the correct network in case more than 1 is active).
     *
     * @throws IllegalStateException
     */
    internal abstract suspend fun <T> checkIsStableWhile(block: suspend () -> T): T

    /**
     * @return  `true` if the network is currently stable, `false` otherwise.
     */
    internal abstract fun isStable(): Boolean

    companion object Key: CoroutineContext.Key<NetworkStabilityChecker> {
        fun CoroutineContext.getNetStabilityChecker(): NetworkStabilityChecker =
            this[NetworkStabilityChecker]
                ?: throw IllegalStateException("coroutine context $this does not provide a `networkStabilityChecker`, but one is needed")
        }
}

