package org.opendc.trace.table.csv

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import org.opendc.trace.ColumnReader
import org.opendc.trace.TableReader
import java.io.File

internal class CSVTableReader(file: File): TableReader {

    /**
     * Using CSVParser instead of mapping iterator to make use of different number types built in parsing funcitons
     */
    private val parser: CsvParser

    private val colReaders = mutableMapOf<String, MutableList<ColumnReader<*, *>>>()

    init {
        val csvMapper = CsvMapper()
        val csvSchema = csvMapper.typedSchemaFor(MutableMap::class.java).withHeader()

        val mappingIter: MappingIterator<List<String>> = csvMapper.readerFor(
            MutableMap::class.java
        ).with(csvSchema.withColumnSeparator(';'))
            .readValues(file)

        parser = mappingIter.parser as CsvParser
        parser.schema.columnNames
    }

    override fun nextLine(): Boolean {
        if (!parser.toNextLine()) return false

        while ((parser.nextValue()?: return true) != JsonToken.END_OBJECT) {
            colReaders[parser.currentName]?.forEach { colReader ->
                // sets the current value of the column (corresponding to the current line)
                colReader.setFromJsonParser(parser)
            }
        }

        return true
    }

    override fun <O, P: Any> addColumnReader(
        name: String,
        columnType: ColumnReader.ColumnType<O>,
        process: (O) -> P,
        postProcess: (P) -> Unit,
    ): ColumnReader<O, P>? {
        return if (name in parser.schema.columnNames) {
            val newReader = ColumnReader(
                columnType = columnType,
                process = process,
                postProcess = postProcess
            )

            colReaders.getOrPut(name) { mutableListOf() }.add(newReader)

            newReader
        } else null
    }

}



private fun CsvParser.toNextLine(): Boolean {
    while (currentToken != null
        && currentToken != JsonToken.START_OBJECT // otherwise first row is skipped
        && currentToken != JsonToken.END_OBJECT
    ) { nextValue() }

    // if (at the end of a row) go to new row
    // else remain at the end of the row
    if (currentToken == JsonToken.END_OBJECT) nextValue()

    return currentToken != null && currentToken != JsonToken.END_OBJECT
}
