package org.opendc.trace.table.csv

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import org.opendc.trace.table.TableReader
import java.io.File


// TODO: handle different separators


internal class CSVTableReader(
    file: File,
    override val tableName: String = file.nameWithoutExtension,
    artificialColumns: Map<String, Any>
): TableReader(artificialCols = artificialColumns) {

    /**
     * Using CSVParser instead of mapping iterator to make use of different number types built in parsing funcitons
     */
    private val parser: CsvParser

    override val nonArtificialColNames: Set<String>

    init {
        val csvMapper = CsvMapper()
        val csvSchema = csvMapper.typedSchemaFor(MutableMap::class.java).withHeader()

        val mappingIter: MappingIterator<List<String>> = csvMapper.readerFor(
            MutableMap::class.java
        ).with(csvSchema.withColumnSeparator(';'))
            .readValues(file)

        parser = mappingIter.parser as CsvParser
        nonArtificialColNames = parser.schema.columnNames.toSet()
    }

    override fun parseNextLine(): Boolean {
        if (!parser.toNextLine()) return false

        while ((parser.nextValue()?: return true) != JsonToken.END_OBJECT) {
            colReaders[parser.currentName]?.forEach { colReader ->
                // sets the current value of the column (corresponding to the current line)
                colReader.setFromJsonParser(parser)
            }
        }

        return true
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
