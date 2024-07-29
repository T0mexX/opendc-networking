/*
 * Copyright (c) 2021 AtLarge Research
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

@file:JvmName("TopologyFactories")

package org.opendc.compute.topology

import org.opendc.compute.topology.specs.ClusterSpec
import org.opendc.compute.topology.specs.HostJSONSpec
import org.opendc.compute.topology.specs.HostSpec
import org.opendc.compute.topology.specs.TopologySpec
import org.opendc.simulator.compute.SimPsuFactories
import org.opendc.simulator.compute.model.MachineModel
import org.opendc.simulator.compute.model.MemoryUnit
import org.opendc.simulator.compute.model.ProcessingNode
import org.opendc.simulator.compute.model.ProcessingUnit
import org.opendc.simulator.compute.power.getPowerModel
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NetworkInterface
import java.io.File
import java.io.InputStream
import java.util.SplittableRandom
import java.util.UUID
import java.util.random.RandomGenerator

/**
 * A [TopologyReader] that is used to read the cluster definition file.
 */
private val reader = TopologyReader()

/**
 * Construct a topology from the specified [pathToFile].
 */
public fun clusterTopology(
    pathToFile: String,
    random: RandomGenerator = SplittableRandom(0),
): List<HostSpec> {
    return clusterTopology(File(pathToFile), random)
}

/**
 * Construct a topology from the specified [file].
 */
public fun clusterTopology(
    file: File,
    random: RandomGenerator = SplittableRandom(0),
): List<HostSpec> {
    val topology = reader.read(file)
    return topology.toHostSpecs(random)
}

/**
 * Construct a topology from the specified [input].
 */
public fun clusterTopology(
    input: InputStream,
    random: RandomGenerator = SplittableRandom(0),
): List<HostSpec> {
    val topology = reader.read(input)
    return topology.toHostSpecs(random)
}

public fun TopologySpec.Companion.fromFile(file: File): TopologySpec =
    reader.read(file)
public fun TopologySpec.Companion.fromPath(path: String): TopologySpec =
    reader.read(File(path))
public fun TopologySpec.Companion.fromInput(input: InputStream): TopologySpec =
    reader.read(input)


/**
 * Helper method to convert a [TopologySpec] into a list of [HostSpec]s.
 */
public fun TopologySpec.toHostSpecs(random: RandomGenerator): List<HostSpec> {
    return clusters.flatMap { cluster ->
        List(cluster.count) {
            cluster.toHostSpecs(random, networkController)
        }.flatten()
    }
}

/**
 * Helper method to convert a [ClusterSpec] into a list of [HostSpec]s.
 */
private var clusterId = 0

private fun ClusterSpec.toHostSpecs(
    random: RandomGenerator,
    networkController: NetworkController?
): List<HostSpec> {
    val hostSpecs =
        hosts.flatMap { host ->
            (
                host.toHostsSpecs(
                    clusterId,
                    random,
                    networkController = networkController
                )
            )
        }
    clusterId++
    return hostSpecs
}

/**
 * Helper method to convert a [HostJSONSpec] into a [HostSpec]s.
 */
private var hostId = 0 // used if no network controller
private var globalCoreId = 0

private fun HostJSONSpec.toHostsSpecs(
    clusterId: Int,
    random: RandomGenerator,
    networkController: NetworkController?
): List<HostSpec> {
    return buildList {
        repeat(count) { hostIndex ->
            val unknownProcessingNode = ProcessingNode("unknown", "unknown", "unknown", cpu.coreCount)
            val units = List(cpu.count) { ProcessingUnit(unknownProcessingNode, globalCoreId++, cpu.coreSpeed) }

            val unknownMemoryUnit = MemoryUnit(memory.vendor, memory.modelName, memory.memorySpeed, memory.memorySize)
            val machineModel =
                MachineModel(
                    units,
                    listOf(unknownMemoryUnit),
                )

            val powerModel =
                getPowerModel(powerModel.modelType, powerModel.power, powerModel.maxPower, powerModel.idlePower)

            val networkInterface: NetworkInterface? =
                // networkController != null  => network specifications were provided and network simulation is requested.
                networkController?.let { controller ->
                    // The id specified in the "nodeIds" property in the Host JSON schema if any.
                    val jsonNodeId = nodeIds.getOrNull(hostIndex)
                    jsonNodeId?.let jsonIdTry@{
                        // The network interface of the node with id corresponding to jsonNodeId if exists.
                        controller.claimNode(nodeId = it)
//                            ?: NetworkController.log.warnAndNull("nodeId $it provided in JSON topology file " +
//                                "does not correspond to any node id of the network defined in the 'networkFile' property (or duplicate ids are provided)." +
//                                "Falling back to first unclaimed host node (might cause a chain of claiming each other node ids)")
                    }
                        // The network interface of any unclaimed host node in the network.
                        ?: controller.claimNextHostNode()
                        // Network controller not null (network simulation required), but no unclaimed host available.
                        ?: throw RuntimeException("'networkFile' property defined in topology file, " +
                            "however not enough host nodes available. Either a different network with a higher " +
                            "number of host nodes has to be provided, or the number of hosts shall be lowered")
                } // networkController == null => not network interface and no network simulation.

            val nodeId: Long = networkInterface?.nodeId ?: (hostId++).toLong()
            val hostName: String = name ?: "Host-${nodeId.toInt()}"

            add(
                HostSpec(
                    UUID(random.nextLong(), nodeId),
                    hostName,
                    mapOf("cluster" to clusterId),
                    machineModel,
                    SimPsuFactories.simple(powerModel),
                    netIface = networkInterface
                )
            )
        }
    }
}
