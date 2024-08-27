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

package org.opendc.simulator.network.playground.cmds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.flow.NetFlow
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.cmds.RmLink.regex
import org.opendc.simulator.network.utils.infoNewLn

/**
 * Logs information regarding the [NetFlow] currently present in the [Network]
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 * > flows
 * 16:52:10.377 [INFO] SHOW_FLOWS -
 * | ==== Flows ====
 * | id   sender    dest      demand              throughput
 * | 0    0         3         1.000 Mbps          1.000 Mbps
 * | 2    0         3         500.000 bps         500.000 bps
 */
internal data object ShowFlows : PGCmd("SHOW_FLOWS") {
    override val regex = Regex("\\s*(?:flows|f)(?:|\\s+(\\d*))\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val network: Network = (coroutineContext[PGEnv]!!.network)

        // No NodeId specified.
        if (result.groupValues[1].isEmpty()) {
            showNetworkFlows(network)
            // NodeId specified.
        } else {
            showNodeFlows(result, network)
        }
    }

    private fun CoroutineScope.showNodeFlows(
        result: MatchResult,
        network: Network,
    ) {
        val nodeId: NodeId = fromStrElseCanc(result.groupValues[1])

        val node: Node = network.getNodeElseCanc(nodeId)

        launch {
            network.awaitStability()
            log.infoNewLn(node.fmtFlows())
        }
    }

    private fun CoroutineScope.showNetworkFlows(network: Network) {
        launch {
            network.awaitStability()
            log.infoNewLn(network.fmtFlows())
        }
    }
}
