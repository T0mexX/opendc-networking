package org.opendc.simulator.network.export

import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.Type

public abstract class ExportField<T: Exportable<T>> {
    internal abstract val fld: Type
    public val fldName: String get() = fld.name
    protected abstract fun RecordConsumer.addValue(exportable: T)

    internal fun RecordConsumer.writeField(exportable: T, index: Int) {
        startField(fldName, index)
        addValue(exportable)
        endField(fldName, index)
    }
}
