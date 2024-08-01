package org.opendc.simulator.network.flow

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.units.Ms
import org.opendc.simulator.network.utils.Kb
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.OnChangeHandler
import org.opendc.simulator.network.utils.SuspOnChangeHandler
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.roundTo0withEps
import kotlin.properties.Delegates

/**
 * Represents an end-to-end flow, meaning the flow from one [EndPointNode] to another.
 * This end-to-end flow can be split into multiple sub-flows along the path,
 * but ultimately each sub-flow arrives at destination.
 * @param[name]                     name of the flow if any.
 * @param[transmitterId]            id of the [EndPointNode] this end-to-end flow is generated from.
 * @param[destinationId]            id of the [EndPointNode] this end-to-end flow is directed to.
 * @param[demand]                   data rate generated by the sender.
 */
public class NetFlow internal constructor(
    public val name: String = DEFAULT_NAME,
    public val transmitterId: NodeId,
    public val destinationId: NodeId,
    demand: Kbps = .0,
    ) {
    public val id: FlowId = nextId

    /**
     * Functions [(NetFlow, Kbps, Kbps) -> Unit] invoked whenever the throughput of the flow changes.
     */
    private val throughputOnChangeHandlers = mutableListOf<OnChangeHandler<NetFlow, Kbps>>()

    /**
     * Functions [(NetFlow, Kbps, Kbps) -> Unit] invoked whenever the demand of the flow changes.
     */
    private val demandOnChangeHandlers = mutableListOf<SuspOnChangeHandler<NetFlow, Kbps>>()

    /**
     * Total data transmitted since the start of the flow (in Kb).
     */
    private var totDataTransmitted: Kb = .0

    /**
     * The current demand of the flow (in Kbps).
     */
    public var demand: Kbps = demand
        private set
    private val demandMutex = Mutex()

    init {
        // Sets up the static destination id retrieval for the flow.
        _flowsDestIds[id] = destinationId
    }

    /**
     * 'Sus' stands for suspending, see [setDemand] for java compatibility.
     *
     * Updates the data rate demand for ***this*** flow.
     * Call observers change handlers, added with [withDemandOnChangeHandler].
     */
    @JvmSynthetic
    public suspend fun setDemandSus(newDemand: Kbps): Unit = demandMutex.withLock {
        val oldDemand = demand
        if (newDemand approx oldDemand) return
        demand = newDemand

        // calls observers handlers
        demandOnChangeHandlers.forEach {
            it.handleChange(this, oldDemand, newDemand)
        }
    }

    /**
     * Non suspending overload for java interoperability.
     */
    public fun setDemand(newDemand: Kbps) { runBlocking { setDemandSus(newDemand) } }

    /**
     * The end-to-end throughput of the flow.
     */
    public var throughput: Kbps = .0
        internal set(new) = runBlocking { throughputMutex.withLock {
            if (new == field) return@runBlocking
            val old = field
            field = if (new approx demand) demand else new.roundTo0withEps()

            throughputOnChangeHandlers.forEach {
                it.handleChange(obj = this@NetFlow, oldValue = old, newValue = field)
            }
        } }
    private val throughputMutex = Mutex()

    /**
     * Adds [f] among the functions invoked whenever the throughput of the flow changes.
     */
    public fun withThroughputOnChangeHandler(f: (NetFlow, Kbps, Kbps) -> Unit): NetFlow {
        throughputOnChangeHandlers.add(f)

        return this
    }

    /**
     * Adds [f] among the functions invoked whenever the demand of the flow changes.
     */
    internal fun withDemandOnChangeHandler(f: SuspOnChangeHandler<NetFlow, Kbps>): NetFlow {
        demandOnChangeHandlers.add(f)

        return this
    }

    /**
     * Advances the time for the flow, updating the total data
     * transmitted according to [ms] milliseconds timelapse.
     */
    internal fun advanceBy(ms: Ms) {

        totDataTransmitted += throughput * ms.secValue()
    }

    /**
     * Invoked by the garbage collector whenever a flow is destroyed.
     * It is deprecated since it does not offer any guarantees to be invoked,
     * but guarantees in these case are not needed, removing some entries
     * is only for slightly improved performances.
     */
    @Suppress("removal")
    protected fun finalize() {
        _flowsDestIds.remove(this.id)
    }

    internal companion object {
        internal const val DEFAULT_NAME: String = "unknown"

        @VisibleForTesting
        internal fun reset() {
            nextId = 0
            _flowsDestIds.clear()
        }

        /**
         * Returns a unique flow id [Long].
         */
        var nextId: FlowId = 0
            get() {
                if (field == FlowId.MAX_VALUE)
                    throw RuntimeException("flow id reached its maximum value")
                field++
                return field - 1
            }
            private set


        /**
         * Stores the [NodeId] of the destination for each [NetFlow]
         * not yet discarded by the garbage collector.
         */
        internal val flowsDestIds: Map<FlowId, NodeId> get() = _flowsDestIds
        private val _flowsDestIds = mutableMapOf<FlowId, NodeId>()
    }
}

/**
 * Type alias for improved understandability.
 */
public typealias FlowId = Long
