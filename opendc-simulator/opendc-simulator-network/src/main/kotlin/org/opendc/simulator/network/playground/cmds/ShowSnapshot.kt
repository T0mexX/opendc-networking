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
import org.opendc.simulator.network.api.snapshots.NodeSnapshot.Companion.snapshot
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.cmds.ShowFlows.regex
import org.opendc.simulator.network.utils.infoNewLn
import java.time.Instant

/**
 * Logs either a [Network] snapshot or a [Node] snapshot.
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 *
 * // network snapshot (not yet implemented)
 * > snapshot
 *
 * // node snapshot
 * > snapshot 1 /* node id */16:57:12.341 [DefaultDispatcher-worker-3] [INFO] SHOW_SNAPSHOT -
 * | node                     instant                       flows in                   flows out                  generating n flows         consuming n flows          curr min flow tput %       curr max flow tput %       curr avrg flow tput %      curr tput (all flows) (%)  curr node port usage %     curr pwr use               avrg pwr over time         tot energy cons
 * | [Switch: id=1]           2024-08-27T14:57:12.3307Z     2                          2                          0                          0                          100.00%                    100.00%                    100.00%                    0.50025 Mbps (100.0%)      4.77076%                   67.89372 Watts             67.89364 Watts             0.67894 KJoule
 */
internal data object ShowSnapshot : PGCmd("SHOW_SNAPSHOT") {
    override val regex = Regex("\\s*(?:snapshot|snap)(?:|\\s+(\\d*))\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val network: Network = (coroutineContext[PGEnv]!!.network)

        // No NodeId specified.
        if (result.groupValues[1].isEmpty()) {
            showNetworkSnapshot()
            // NodeId specified.
        } else {
            showNodeSnapshot(result, network)
        }
    }

    private fun CoroutineScope.showNodeSnapshot(
        result: MatchResult,
        network: Network,
    ) {
        val nodeId: NodeId = fromStrElseCanc(result.groupValues[1])

        val node: Node = network.getNodeElseCanc(nodeId)

        launch {
            log.infoNewLn(
                node.snapshot(
                    instant = Instant.now(),
                    withStableNetwork = network,
                    noCache = true,
                ).fmt(),
            )
        }
    }

    private fun showNetworkSnapshot() {
        // TODO: implement
        log.error("not yet implemented")
    }
}
