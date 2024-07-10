package org.opendc.trace

import org.opendc.trace.table.Table

public interface Trace {

    public val tablesByName: Map<String, Table>

}
