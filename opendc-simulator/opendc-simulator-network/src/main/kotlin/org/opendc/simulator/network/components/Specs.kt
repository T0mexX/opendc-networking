package org.opendc.simulator.network.components

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

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


    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun <T> fromFile(file: File): Specs<T> {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromStream(file.inputStream())
        }

        fun <T> fromFile(filePath: String): Specs<T> =
            fromFile(File(filePath))

    }
}
