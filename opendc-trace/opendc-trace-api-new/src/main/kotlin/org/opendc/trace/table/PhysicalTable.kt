package org.opendc.trace.table

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

public class Table private constructor(
    public val name: String,
    private val file: File
) {

    public val sizeInBytes: Long
        get() = Files.size(file.toPath())

    public fun getReader(): TableReader =
        TableReader.genericReaderFor(file)
            ?: throw RuntimeException("table error, file path no longer valid")

    public companion object {
        public fun withNameFromFile(name: String, file: File): Table? =
                // check if a table reader can be instantiated for the file
                TableReader.genericReaderFor(file)?.let {
                    Table(name = name, file = file)
                }

        public fun withNameFromFile(name: String, path: Path): Table? =
            withNameFromFile(name = name, file = path.toFile())

        public fun withNameFromFile(name: String, path: String): Table? =
            withNameFromFile(name = name, file = File(path))
    }
}
