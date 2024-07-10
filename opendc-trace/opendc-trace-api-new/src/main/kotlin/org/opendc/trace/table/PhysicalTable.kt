package org.opendc.trace.table

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

public class PhysicalTable internal constructor(
    override val name: String,
    private val file: File
): Table() {

    override val sizeInBytes: Long
        get() = Files.size(file.toPath())

    override fun getReader(): TableReader =
        TableReader.genericReaderFor(file = file, artificialColumns = artificialCols, tableName = name)
            ?: throw RuntimeException("table error, file path no longer valid")
}