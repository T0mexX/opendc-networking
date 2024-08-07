package org.opendc.simulator.network.export

import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.Type
import org.opendc.simulator.network.utils.logger

public abstract class ExportField<T: Exportable<T>> {
    public abstract val fld: Type
    public val fldName: String get() = fld.name
    protected abstract fun RecordConsumer.addValue(exportable: T)

    @PublishedApi
    internal fun RecordConsumer.writeField(exportable: T, index: Int) {
        startField(fldName, index)
        addValue(exportable)
        endField(fldName, index)
    }

    private companion object { val log by logger() }
}
