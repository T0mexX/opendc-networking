package org.opendc.simulator.network.api.snapshots

import org.opendc.simulator.network.utils.Flags

public abstract class Snapshot<T> {

    protected abstract val dfltColWidth: Int

    public abstract fun fmt(flags: Flags<T> = Flags.all()): String

    public abstract fun fmtHdr(flags: Flags<T> = Flags.all()): String

    protected fun StringBuilder.appendPad(obj: Any?, pad: Int = dfltColWidth) {
        append(obj.toString().padEnd(pad))
    }

    protected fun StringBuilder.appendPad(str: String, pad: Int = dfltColWidth) {
        append(str.padEnd(pad))
    }
}
