package org.opendc.trace.table.column

import org.opendc.trace.table.column.ColumnReader.ColumnType

public abstract class Column<O> {
    internal abstract val name: String
    internal abstract val type: ColumnType<O>
}
