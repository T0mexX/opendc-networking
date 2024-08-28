/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
    artificialColumns: Map<String, Any>,
) : TableReader(artificialCols = artificialColumns) {
    /**
     * Using CSVParser instead of mapping iterator to make use of different number types built in parsing funcitons
     */
    private val parser: CsvParser

    override val nonArtificialColNames: Set<String>

    init {
        val csvMapper = CsvMapper()
        val csvSchema = csvMapper.typedSchemaFor(MutableMap::class.java).withHeader()

        val mappingIter: MappingIterator<List<String>> =
            csvMapper.readerFor(
                MutableMap::class.java,
            ).with(csvSchema.withColumnSeparator(';'))
                .readValues(file)

        parser = mappingIter.parser as CsvParser
        nonArtificialColNames = parser.schema.columnNames.toSet()
    }

    override fun parseNextLine(): Boolean {
        if (!parser.toNextLine()) return false

        while ((parser.nextValue() ?: return true) != JsonToken.END_OBJECT) {
            colReaders[parser.currentName]?.forEach { colReader ->
                // sets the current value of the column (corresponding to the current line)
                colReader.setFromJsonParser(parser)
            }
        }

        return true
    }
}

private fun CsvParser.toNextLine(): Boolean {
    while (currentToken != null &&
        currentToken != JsonToken.START_OBJECT && // otherwise first row is skipped
        currentToken != JsonToken.END_OBJECT
    ) {
        nextValue()
    }

    // if (at the end of a row) go to new row
    // else remain at the end of the row
    if (currentToken == JsonToken.END_OBJECT) nextValue()

    return currentToken != null && currentToken != JsonToken.END_OBJECT
}
