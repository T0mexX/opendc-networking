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
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.cmds.NewSwitch.regex
import org.opendc.simulator.network.utils.infoNewLn

/**
 * Displays [Node] information.
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 * > node 0
 * 16:46:14.078 [INFO] NODE_INFO -
 * | Type = HostNode
 * | numPorts = 1000
 * | portSpeed = 10.485760 Mbps
 * | numConnectedNodes = 2
 */
internal data object NodeInfo : PGCmd("NODE_INFO") {
    override val regex = Regex("\\s*(?:n|node)\\s+([^ ]+)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val network: Network = (coroutineContext[PGEnv]!!.network)

        val nodeId: NodeId = ifInternetElseNull(result.groupValues[1]) ?: fromStrElseCanc(result.groupValues[1])

        val node: Node = network.getNodeElseCanc(nodeId)

        launch {
            network.awaitStability()
            log.infoNewLn(node.fmt())
        }
    }
}
