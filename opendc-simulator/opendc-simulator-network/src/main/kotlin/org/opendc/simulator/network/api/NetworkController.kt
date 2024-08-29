/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.network.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.common.units.DataRate
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.api.workload.SimNetWorkload
import org.opendc.simulator.network.components.EndPointNode
import org.opendc.simulator.network.components.HostNode
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.components.Network.Companion.getNodesById
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.Specs
import org.opendc.simulator.network.export.NetExportHandler
import org.opendc.simulator.network.export.NetworkExportConfig
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.utils.errAndNull
import org.opendc.simulator.network.utils.infoNewLn
import org.opendc.simulator.network.utils.logger
import org.slf4j.Logger
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.UUID

/**
 * Interface through which control the network.
 *
 * @param[network]          the network controlled by this controller.
 * @param[instantSource]    the external instantSource (time source). If an external
 * time source is set, one can synchronize the network statistics with [sync].
 * If no external time source is provided, the controller defaults to an internal one starting at EPOCH.
 * Internal time can then be set with [setInternalTime].
 * @param[exportConfig]     the export configuration for the simulation if any. It can then be changed with [setExportConfig]
 */
public class NetworkController(
    internal val network: Network,
    instantSource: InstantSource? = null,
    exportConfig: NetworkExportConfig? = null,
) : AutoCloseable {
    public companion object {
        public val log: Logger by logger()

        /**
         * Secondary constructor that instantiate both the [Network] (from [file]),
         * and the [NetworkController].
         *
         * @throws[NoSuchFileException]
         */
        @ExperimentalSerializationApi
        public fun fromFile(
            file: File,
            instantSource: InstantSource? = null,
        ): NetworkController {
            val jsonReader = Json { ignoreUnknownKeys = true }
            val netSpec = jsonReader.decodeFromStream<Specs<Network>>(file.inputStream())
            return NetworkController(netSpec.build(), instantSource)
        }

        /**
         * @see[fromFile]
         */
        @OptIn(ExperimentalSerializationApi::class)
        public fun fromPath(
            path: String,
            instantSource: InstantSource? = null,
        ): NetworkController = fromFile(File(path), instantSource)
    }

    private var netExportHandler: NetExportHandler? =
        exportConfig?.let {
            NetExportHandler(config = it)
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
     * The current instant of the network time source.
     */
    public val currentInstant: Instant
        get() = instantSrc.instant()

    /**
     * The [Instant] when the last [advanceBy] or [sync] was called.
     * It is used by sync to determine the time span to advance the network by.
     */
    internal var lastUpdate: Time = instantSrc.time

    /**
     * @see[NetEnRecorder]
     */
    public val energyRecorder: NetEnRecorder =
        NetEnRecorder(network)

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
    public val claimedHostIds: Set<NodeId> get() = _claimedHostIds
    private val _claimedHostIds = mutableSetOf<NodeId>()

    init {
        instantSource?.let { lastUpdate = Time.ofInstantFromEpoch(it.instant()) }

        network.launch()
        log.info(network.fmtNodes())
    }

    /**
     * Sets [instantSource] as the external time source of the network. The network is then to be synchronized with [sync].
     */
    public fun setInstantSource(instantSource: InstantSource) {
        instantSrc = NetworkInstantSrc(instantSource)
        lastUpdate = instantSrc.time
    }

    /**
     * Sets [time] as the internal time of the network. The network time is then to be advanced with [advanceBy].
     * @throws[IllegalArgumentException] if the current [instantSrc] is external.
     */
    internal fun setInternalTime(time: Time) {
        instantSrc.setInternalTime(time)
        lastUpdate = time
    }

    /**
     * Sets [exportConfig] as the export configuration, replacing any previously set configuration.
     */
    public fun setExportConfig(exportConfig: NetworkExportConfig) {
        netExportHandler?.let {
            close()
            log.warn("replacing current network export handler $it with new one")
        }

        netExportHandler =
            NetExportHandler(config = exportConfig)
    }

    /**
     * See [claimedHostIds] for an explanation of the *claiming process*.
     *
     * @return the network interface of an unclaimed [HostNode] if exists, `null` otherwise.
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
     * Maps "virtual" node id [from] to physical id [to].
     *
     * Used only when a [SimNetWorkload] is executed with virtual mapping.
     */
    internal fun virtualMap(
        from: NodeId,
        to: NodeId,
    ) {
        if (from in virtualMapping) {
            log.warn("overriding mapping of virtual node id $from")
        }

        virtualMapping[from] = to
    }

    /**
     * Invokes [claimNode] with [UUID.node] value as [NodeId].
     */
    public fun claimNode(uuid: UUID): NetworkInterface? = claimNode(uuid.node())

    /**
     * @return the network interface of [Node] with id [nodeId] if it
     * exists (can start/receive flows) and it is yet unclaimed, `null` otherwise.
     */
    public fun claimNode(nodeId: NodeId): NetworkInterface? {
        // Check that node is not already claimed.
        if (nodeId in _claimedHostIds) {
            return log.errAndNull("unable to claim node nodeId $nodeId, nodeId already claimed")
        }

        // If node is host.
        network.getNodesById<HostNode>()[nodeId]
            ?.let {
                _claimedHostIds.add(nodeId)
                return getNetInterfaceOf(nodeId)
            }

        // else
        return log.errAndNull(
            "unable to claim node nodeId $nodeId, " +
                "nodeId not existent or not associated to a host node",
        )
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
     * @return the newly started flow if it was started successfully, `null` otherwise.
     */
    public suspend fun startFlow(
        transmitterId: NodeId,
        destinationId: NodeId = internetNetworkInterface.nodeId,
        demand: DataRate = DataRate.ZERO,
        name: String = NetFlow.DEFAULT_NAME,
        throughputOnChangeHandler: ((NetFlow, DataRate, DataRate) -> Unit)? = null,
    ): NetFlow? {
        val mappedTransmitterId: NodeId = mappedOrSelf(transmitterId)
        val mappedDestId: NodeId = mappedOrSelf(destinationId)

        val netFlow =
            NetFlow(
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
     * @return the flow itself if it has been started successfully, `null` otherwise.
     */
    private suspend fun startFlow(netFlow: NetFlow): NetFlow? {
        if (netFlow.transmitterId !in network.endPointNodes) {
            return log.errAndNull(
                "unable to start network flow from node ${netFlow.transmitterId}, " +
                    "node does not exist or is not an end-point node",
            )
        }

        if (netFlow.destinationId !in network.endPointNodes) {
            return log.errAndNull(
                "unable to start network flow directed to node ${netFlow.destinationId}, " +
                    "node does not exist or it is not an end-point-node",
            )
        }

        if (netFlow.id in network.flowsById.keys) {
            return log.errAndNull(
                "unable to start network flow with flow id ${netFlow.id}, " +
                    "a flow that id already exists",
            )
        }

        network.startFlow(netFlow)

        return netFlow
    }

    /**
     * Stops the [NetFlow] with id [flowId] if it is present in the network.
     *
     * @return the [NetFlow] that was terminated if any, `null` otherwise.
     */
    public suspend fun stopFlow(flowId: FlowId): NetFlow? = network.stopFlow(flowId)

    /**
     * @return the network interface of node with id [nodeId] if it exists,
     * independently of the fact that it was already claimed or not (used internally), `null` otherwise.
     */
    private fun getNetInterfaceOf(nodeId: NodeId): NetworkInterface? =
        network.endPointNodes[nodeId]?.let {
            NetworkInterface(node = it, netController = this, owner = "physical node $nodeId")
        } ?: log.errAndNull(
            "unable to retrieve network interface, " +
                "node does not exist or does not provide an interface",
        )

    /**
     * If the network time source is external (see [instantSrc]), the network time is advanced to sync with the source.
     * At the end of this method the network is stable (meaning its flows are stable).
     *
     * Avoid invoking this method when the external time source is set, and it
     * did not change since last update, unless network stability is needed (for performance reasons).
     * @param[logSnapshot]          if `true` it logs the [NetworkSnapshot] after the synchronization, at INFO level.
     * has a throughput higher than its demand.
     */
    @JvmOverloads
    public fun sync(logSnapshot: Boolean = false) {
        val syncTo = instantSrc.time
        if (instantSrc.isExternalSource) {
            runBlocking(network.validator) { network.awaitStability() }
            if (logSnapshot) log.infoNewLn(snapshot().fmt())

            val timeSpan = syncTo - lastUpdate
            if (timeSpan == Time.ZERO) return
            runBlocking(network.validator) { advanceBy(timeSpan, suppressWarn = true) }
        } else {
            log.error("unable to synchronize network, instant source not set. Use 'advanceBy()' instead")
        }
    }

    /**
     * Advances the network time by [duration], updating network related statistics.
     */
    public fun advanceBy(duration: Duration) {
        runBlocking { advanceBy(Time.ofDuration(duration)) }
    }

    /**
     * Advances the network time by [time] milliseconds, updating network related statistics.
     */
    public suspend fun advanceBy(time: Time) {
        advanceBy(time, suppressWarn = false)
    }

    private suspend fun advanceBy(
        time: Time,
        suppressWarn: Boolean,
    ) {
        suspend fun advance(jump: Time) {
            network.awaitStability()
            network.validator.checkIsStableWhile {
                if (instantSrc.isInternalSource) {
                    instantSrc.advanceTime(jump)
                }

                network.advanceBy(jump)
                energyRecorder.advanceBy(jump)
                lastUpdate += jump
            }
        }

        // If method called externally while external instant source is set.
        if (instantSrc.isExternalSource && suppressWarn.not()) {
            log.warn(
                "advancing time directly while instant source is set, this can cause ambiguity. " +
                    "You can synchronize the network with the instant source with 'sync()'",
            )
        }

        if (time < Time.ZERO) return log.error("advanceBy received negative time-span parameter($time), ignoring...")
        if (time == Time.ZERO) return

        netExportHandler?.let {
            // Advances time in multiple steps to allow all export deadlines.
            with(it) {
                var remaining: Time = time
                while (remaining != Time.ZERO) {
                    val nextJump = remaining min timeUntilExport()
                    advance(nextJump)
                    exportIfNeeded()
                    remaining -= nextJump
                }
            }
        } ?: advance(time)
    }

    /**
     * @return the physical [NodeId] to which [id] is mapped if any, [id] otherwise.
     */
    private fun mappedOrSelf(id: NodeId): NodeId = virtualMapping[id] ?: let { id }

    /**
     * Cancels the coroutine that runs the network.
     */
    override fun close() {
        network.runnerJob?.cancel()
        netExportHandler?.close()
    }
}
