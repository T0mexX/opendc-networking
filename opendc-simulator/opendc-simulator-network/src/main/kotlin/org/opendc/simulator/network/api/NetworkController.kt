package org.opendc.simulator.network.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.AVRG_THROUGHPUT
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.ENERGY
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.FLOWS
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.INSTANT
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.TOT_THROUGHPUT
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.CoreSwitch
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.energy.EnergyConsumer
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.logger
import org.opendc.simulator.network.utils.ms
import org.opendc.simulator.network.api.simworkloads.SimNetWorkload
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.INTERNET_ID
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.utils.approx
import org.opendc.simulator.network.utils.approxLarger
import org.opendc.simulator.network.utils.errAndNull
import org.slf4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.UUID

/**
 * Interface through which control the network (from external modules).
 *
 * @param[network]          the network controlled by this controller.
 * @param[instantSource]    the external instantSource (time source). If an external
 * time source is set, one can synchronize the network statistics with [sync].
 *
 * If no external time source is provided, the controller defaults to an internal one starting at EPOCH.
 * If a [SimNetWorkload] is provided with [execWorkload], and the reset
 * parameter is set to `true`, the internal time source is set to the start of the workload.
 */
public class NetworkController(
    internal val network: Network,
    instantSource: InstantSource? = null,
): AutoCloseable {
    public companion object {
        public val log: Logger by logger()

        /**
         * Secondary constructor that instantiate both the [Network] (from [file]),
         * and the [NetworkController].
         *
         * @throws[NoSuchFileException]
         */
        @ExperimentalSerializationApi
        public fun fromFile(file: File, instantSource: InstantSource? = null): NetworkController {
            val jsonReader = Json() { ignoreUnknownKeys = true }
            val netSpec = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            return NetworkController(netSpec.build(), instantSource)
        }

        /**
         * @see[fromFile]
         */
        @OptIn(ExperimentalSerializationApi::class)
        public fun fromPath(path: String, instantSource: InstantSource? = null): NetworkController =
            fromFile(File(path))

        private var bo : Long = 0
        private var nano: Long = 0
    }

    /**
     * If a [SimNetWorkload] is to be executed, the node ids of the workload might
     * not correspond to those of the physical network.
     *
     * Maps the node ids of the current executing workload (if any) to
     * the physical [NodeId]s of the network.
     *
     * This property is unused except when [execWorkload] is called with virtual mapping on.
     * ```kotlin
     * execWorkload(<workload>, withVirtualMapping = true)
     * ```
     */
    private val virtualMapping = mutableMapOf<Long, NodeId>()

    /**
     * The time source of the network. It can be both internal or external (see [setInstantSource]).
     * - If external, then the network is to be synchronized with [sync].
     * - If internal, then network time can be advanced with [advanceBy].
     * - If a workload is being executed (time source internal) than it is handled automatically.
     */
    internal var instantSrc: NetworkInstantSrc = NetworkInstantSrc(instantSource)
        private set

    /**
     * Sets the external time source of the network. The network is then to be synchronized with [sync].
     */
    public fun setInstantSource(instantSource: InstantSource) {
        instantSrc = NetworkInstantSrc(instantSource)
    }

    /**
     * The current instant of the network time source.
     */
    public val currentInstant: Instant
        get() = instantSrc.instant()

    /**
     * The [Instant] when the last [advanceBy] or [sync] was called.
     * It is used by sync to determine the time span to advance the network by.
     */
    private var lastUpdate: Instant = Instant.EPOCH

    init {
        instantSource?.let { lastUpdate = it.instant() }

        network.launch()
        log.info(network.nodesFmt())
    }

    /**
     * @see[NetworkEnergyRecorder]
     */
    public val energyRecorder: NetworkEnergyRecorder =
        NetworkEnergyRecorder(network.nodes.values.filterIsInstance<EnergyConsumer<*>>())

    /**
     * The 'network interface' of the INTERNET abstract node.
     * It can be used to start, update, stop flows that come from the internet.
     *
     * A flow from the internet directed to a [Node] can also be started by the node's
     * [NetworkInterface] with [NetworkInterface.fromInternet].
     */
    public val internetNetworkInterface: NetworkInterface =
        getNetInterfaceOf(INTERNET_ID)
            ?: throw IllegalStateException("network did not initialize the internet abstract node correctly")

    /**
     * Tracks the physical hosts that have been claimed.
     *
     * The claiming process happens when for example a *Host* wants
     * to be associated with a [HostNode] in the network, then it claims its [NetworkInterface].
     * Each node/interface can be claimed only once.
     *
     * - If the host knows which physical node to claim it can invoke [claimNode].
     * - If the host does not know/care which physical node to claim it can invoke [claimNextHostNode].
     */
    internal val claimedHostIds: Set<NodeId> get() = _claimedHostIds
    private val _claimedHostIds = mutableSetOf<NodeId>()

    /**
     * @see[claimedHostIds]
     */
    private val claimedCoreSwitchIds = mutableSetOf<NodeId>()

    /**
     * See [claimedHostIds] for an explanation of the *claiming process*.
     *
     * @return  the network interface of an unclaimed [HostNode] if exists, `null` otherwise.
     */
    public fun claimNextHostNode(): NetworkInterface? {
        val hostsById = network.getNodesById<HostNode>()
        return hostsById.keys
            .filterNot { it in _claimedHostIds }
            .firstOrNull()
            ?.let {
                _claimedHostIds.add(it)
                getNetInterfaceOf(it)
            } ?: log.errAndNull("unable to claim host node, none available")
    }

    /**
     * See [claimedHostIds] for an explanation of the *claiming process*.
     *
     * @return  the network interface of an unclaimed [CoreSwitch] if exists, `null` otherwise.
     */
    public fun claimNextCoreNode(): NetworkInterface? {
        val coreSwitches = network.getNodesById<CoreSwitch>()
        return coreSwitches
            .keys
            .filterNot { it in _claimedHostIds }
            .firstOrNull()
            ?.let {
                claimedCoreSwitchIds.add(it)
                getNetInterfaceOf(it)
            } ?: log.errAndNull("unable to claim core switch node, none available")
    }

    /**
     * Maps "virtual" node id [from] to physical id [to].
     *
     * Used only when a [SimNetWorkload] is executed with virtual mapping (see [execWorkload]).
     */
    internal fun virtualMap(from: NodeId, to: NodeId) {
        if (from in virtualMapping)
            log.warn("overriding mapping of virtual node id $from")

        virtualMapping[from] = to
    }

    /**
     * Invokes [claimNode] with [UUID.node] value as [NodeId].
     */
    public fun claimNode(uuid: UUID): NetworkInterface? =
        claimNode(uuid.node())

    /**
     * @return  the network interface of [Node] with id [nodeId] if it
     * exists (can start/receive flows) and it is yet unclaimed, `null` otherwise.
     */
    public fun claimNode(nodeId: NodeId): NetworkInterface? {
        // Check that node is not already claimed.
        if (nodeId in _claimedHostIds || nodeId in claimedCoreSwitchIds)
            return log.errAndNull("unable to claim node nodeId $nodeId, nodeId already claimed")

        // If node is host.
        network.getNodesById<HostNode>()[nodeId]
            ?.let {
                _claimedHostIds.add(nodeId)
                return getNetInterfaceOf(nodeId)
            }

        // If node is core switch.
        network.getNodesById<CoreSwitch>()[nodeId]
            ?.let {
                claimedCoreSwitchIds.add(nodeId)
                return getNetInterfaceOf(nodeId)
            }

        // else
        return log.errAndNull("unable to claim node nodeId $nodeId, " +
            "nodeId not existent or not associated to a core switch or a host node")
    }

    /**
     * Starts a [NetFlow] if it can be started with the provided parameters.
     *
     * @param[name]                         name of the flow if any.
     * @param[transmitterId]                id of the [EndPointNode] that is to transmit the flow.
     * @param[destinationId]                id of the [EndPointNode] that is to receive the flow.
     * @param[demand]                       the initial demand of the flow (can then be updated with [NetFlow.setDemand]).
     * @param[throughputOnChangeHandler]    a lambda to be invoked whenever the throughput of the flow changes, with the
     * flow itself as first param, the old value as second and the new value as third.
     *
     * @return  the newly started flow if it was started successfully, `null` otherwise.
     */
    public suspend fun startFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId, // TODO: understand how multiple core switches work
        demand: Kbps = .0,
        name: String = NetFlow.DEFAULT_NAME,
        throughputOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null,
    ): NetFlow? {
        val mappedTransmitterId: NodeId =   mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        val netFlow = NetFlow(
                transmitterId = mappedTransmitterId,
                destinationId = mappedDestId,
                name = name,
                demand = demand,
            )

        throughputOnChangeHandler?.let {
            netFlow.withThroughputOnChangeHandler(throughputOnChangeHandler)
        }

        return startFlow(netFlow)
    }

    /**
     * Starts [netFlow] if it can be started.
     *
     * @return  the flow itself if it has been started successfully, `null` otherwise.
     */
    private suspend fun startFlow(netFlow: NetFlow): NetFlow? {
        if (netFlow.transmitterId !in network.endPointNodes)
            return log.errAndNull("unable to start network flow from node ${netFlow.transmitterId}, " +
                "node does not exist or is not an end-point node")

        if (netFlow.destinationId !in network.endPointNodes)
            return log.errAndNull("unable to start network flow directed to node ${netFlow.destinationId}, " +
                "node does not exist or it is not an end-point-node")

        if (netFlow.id in network.flowsById.keys)
            return log.errAndNull("unable to start network flow with flow id ${netFlow.id}, " +
                "a flow that id already exists")

        network.startFlow(netFlow)

        return netFlow
    }

    /**
     * **The usage if this method assumes there can only
     * be 1 flow between 2 nodes (1 per direction).**
     *
     * - If a [NetFlow] with transmitter [transmitterId] and destination
     * [destinationId] already exists in the network then it is updated with the new demand [demand].
     * - If no [NetFlow] with this "signature" exists (and it can be started), then it is started (see [startFlow]).
     *
     * @return  the [NetFlow] (update/created) if its update/creation was successful,
     * `null` if the flow was not in the network, and it could not be started.
     */
    public suspend fun startOrUpdateFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId,
        demand: Kbps = .0,
        dataRateOnChangeHandler: ((NetFlow, Kbps, Kbps) -> Unit)? = null,
    ): NetFlow? {
        val mappedTransmitterId: NodeId = mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        return network.flowsById.values.find {
            it.transmitterId == mappedTransmitterId && it.destinationId == mappedDestId
        } ?. let {
            it.setDemand(demand)
            if (dataRateOnChangeHandler != null) it.withThroughputOnChangeHandler(dataRateOnChangeHandler)

            it
        } ?: startFlow(
                transmitterId = transmitterId,
                destinationId = destinationId,
                demand = demand,
                throughputOnChangeHandler = dataRateOnChangeHandler
        )
    }

    /**
     * Stops the [NetFlow] with id [flowId] if it is present in the network.
     *
     * @return the [NetFlow] that was terminated if any, `null` otherwise.
     */
    public suspend fun stopFlow(flowId: FlowId): NetFlow? =
        network.stopFlow(flowId)

    /**
     * @return      the network interface of node with id [nodeId] if it exists,
     * independently of the fact that it was already claimed or not (used internally), `null` otherwise.
     */
    private fun getNetInterfaceOf(nodeId: NodeId): NetworkInterface? =
        network.endPointNodes[nodeId]?.let {
            NetworkInterface(node = it, netController = this, owner = "physical node $nodeId")
        } ?: log.errAndNull("unable to retrieve network interface, " +
                "node does not exist or does not provide an interface")

    /**
     * If the network time source is external (see [instantSrc]), the network time is advanced to sync with the source.
     * At the end of this method the network is stable (meaning its flows are stable).
     *
     * Avoid invoking this method when the external time source is set, and it
     * did not change since last update, unless network stability is needed (for performance reasons).
     * @param[logSnapshot]          if `true` it logs the [NetworkSnapshot] after the synchronization, at INFO level.
     * @param[consistencyCheck]     if `true` checks that no [NetFlow], after network has reached stability,
     * has a throughput higher than its demand.
     */
    @JvmOverloads
    public fun sync(logSnapshot: Boolean = false, consistencyCheck: Boolean = true) {
        if (instantSrc.isExternalSource) {
            runBlocking { network.awaitStability() }
            if (consistencyCheck) runBlocking { checkFlowConsistency() }
            if (logSnapshot) log.info("\n" + snapshot().fmt(NetworkSnapshot.ENERGY))

            val timeSpan = instantSrc.millis() - lastUpdate.toEpochMilli()
            if (timeSpan == 0L) return
            advanceBy(timeSpan, suppressWarn = true)
        } else log.error("unable to synchronize network, instant source not set. Use 'advanceBy()' instead")
    }

    /**
     * Advances the network time by [duration], updating network related statistics.
     */
    public fun advanceBy(duration: Duration) {
        advanceBy(duration.toMillis())
    }

    /**
     * Advances the network time by [ms] milliseconds, updating network related statistics.
     */
    public fun advanceBy(ms: ms) { advanceBy(ms, suppressWarn = false) }
    private fun advanceBy(ms: ms, suppressWarn: Boolean) {
        if (ms < 0) return log.error("advanceBy received negative time-span parameter($ms), ignoring...")
        if (ms == 0L) return
        runBlocking { network.awaitStability() }

        if (instantSrc.isInternalSource)
            instantSrc.advanceTime(ms)
        else  if (!suppressWarn)
            log.warn("advancing time directly while instant source is set, this can cause ambiguity. " +
                "You can synchronize the network with the instant source with 'sync()'")

        network.advanceBy(ms)
        energyRecorder.advanceBy(ms)
        lastUpdate = instantSrc.instant()
    }

    /**
     * Executes a [SimNetWorkload] on this network.
     *
     * **External time source is overwritten with an internal one**.
     *
     * @param[netWorkload]          the workload to be executed.
     * @param[reset]                if `true`, the network state is reset and
     * its internal time source is set to the start of the workload.
     * @param[withVirtualMapping]   if `true`, it performs virtual mapping of the
     * workload node ids to the physical network node ids (see [virtualMapping]).
     */
    public fun execWorkload(
        netWorkload: SimNetWorkload,
        reset: Boolean = true,
        withVirtualMapping: Boolean = true
    ) {
        if (netWorkload.hasNext().not()) return log.error("network workload empty")

        if (instantSrc.isExternalSource) {
            log.warn("network controller external time source will " +
                "be replaced with an internal one to tun the workload.")
            // If time source was external, an internal time source with same timestamp is set.
            // This time stamp is then overwritten if `reset` is `true`.
            instantSrc = NetworkInstantSrc(internal = instantSrc.instant().toEpochMilli())
        }

        if (reset) {
            network.resetFlows()
//            network.launch() //TODO
            instantSrc.setInternalTime(netWorkload.peek().deadline)
            energyRecorder.reset()
            _claimedHostIds.clear()
            claimedCoreSwitchIds.clear()
            virtualMapping.clear()
        }

        if (withVirtualMapping) netWorkload.performVirtualMappingOn(this)

        runBlocking {
            val pb: ProgressBar = ProgressBarBuilder()
                .setInitialMax(netWorkload.size.toLong())
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName("Simulating...").build()

            delay(1000)

            with (netWorkload) {
                while (hasNext()) {
                    val nextDeadline = peek().deadline

                    // Executes all network events up until `nextDeadline`
                    // included (with all events with that deadline).
                    pb.stepBy(execUntil(nextDeadline))
                    network.awaitStability()
                    log.trace(snapshot().fmt(ENERGY or AVRG_THROUGHPUT or TOT_THROUGHPUT or FLOWS or INSTANT))
                }
            }
            pb.refresh()
        }
    }

    /**
     * @return the physical [NodeId] to which [id] is mapped if any, [id] otherwise.
     */
    private fun mappedOrSelf(id: NodeId): NodeId =
        virtualMapping[id] ?: let { id }

    /**
     * Cancels the coroutine that runs the network.
     */
    override fun close() {
        network.runnerJob?.cancel()
    }

    /**
     * Checks (after network is stabilized) that no flow has a higher throughput than its demand.
     */
    private suspend fun checkFlowConsistency() {
        network.awaitStability()

        network.flowsById.values.forEach {
            check(it.demand approxLarger it.throughput || it.demand approx it.throughput)
            {" Inconsistent state: flow ${it.id} has demand=${it.demand} and throuput=${it.throughput}"}
        }
    }
}

