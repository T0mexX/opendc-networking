package org.opendc.trace.simtrace

import org.opendc.trace.Table
import org.opendc.trace.TableReader

public interface SimTrace<W: SimWorkload> {

    public fun createWorkload(): W


    public interface Builder<T, W> where T : SimTrace<W>, W: SimWorkload {
        public fun parseAndAddLine(tr: TableReader)
        public fun build(): T
        public fun addAllFromTable(t: Table): Builder<T, W> {
            val tr: TableReader = t.newReader()
            while (tr.nextRow())
                parseAndAddLine(tr)

            return this
        }
    }

}
