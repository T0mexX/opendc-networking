package org.opendc.trace.table.column

import org.opendc.trace.table.column.ColumnReader.ColumnType

public abstract class Column<O> {
    public abstract val name: String
    public abstract val type: ColumnType<O>
}
