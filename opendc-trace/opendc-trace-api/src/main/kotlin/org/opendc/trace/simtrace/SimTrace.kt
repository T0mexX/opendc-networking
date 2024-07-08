package org.opendc.trace.simtrace

import org.opendc.trace.TableReader

public interface SimTrace<W: SimWorkload> {

    public fun createWorkload(start: Long): W

    public fun createWorkload(start: Long, checkpointTime: Long, checkpointWait: Long): W


    public interface Builder<T: SimTrace<SimWorkload>> {
        public fun parseAndAddLine(tr: TableReader)
        public fun build(): T
        public fun parseAndAddAll(tr: TableReader)
    }
}
