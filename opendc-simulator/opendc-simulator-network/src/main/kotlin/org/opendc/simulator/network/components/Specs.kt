package org.opendc.simulator.network.components

import kotlinx.serialization.Serializable

/**
 * Type serializable from json, representing the specifics of concrete object of type `T`.
 * The object of type `T` can then be built with [buildFromSpecs].
 */
@Serializable
internal sealed interface Specs<T> { // Needs to be sealed, otherwise serialization won't work
    /**
     * Builds the corresponding object of type `T`
     */
    fun buildFromSpecs(): T
}

