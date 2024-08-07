package org.opendc.simulator.network.flow

import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.UpdateChl
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.ifNullZero


/**
 * Represents a data rate demand update in terms of delta rate by id.
 * These updates are sent into the [UpdateChl] of the [Node] to be processed.
 *
 * An update with delta rates of 0 will reapply the fairness policy of the [Node].
 * Needed in case the topology changes.
 *
 * A value class offers higher type safety than a typealias with extension functions.
 */
@JvmInline
internal value class RateUpdt(private val updt: Map<FlowId, DataRate>): Map<FlowId, DataRate> by updt {
    constructor(p: Pair<FlowId, DataRate>): this(mapOf(p))
    constructor(fId: FlowId, deltaRate: DataRate): this(mapOf(fId to deltaRate))

    companion object {
        fun Map<FlowId, DataRate>.toRateUpdt(): RateUpdt =
            RateUpdt(this)

        @JvmName("toRateUpdtFromMutable") // otherwise methods have same signature on the jvm
        fun MutableMap<FlowId, DataRate>.toRateUpdt(): RateUpdt =
            RateUpdt(this.toMap())
    }

    fun merge(other: RateUpdt): RateUpdt =
        RateUpdt((updt.keys + other.updt.keys)
            .associateWith {
                ((updt[it].ifNullZero()) + (other.updt[it].ifNullZero()))
            })
}
