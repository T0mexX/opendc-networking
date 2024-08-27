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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.CustomNetwork
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.link.Link
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.cmds.RmLink.regex

/**
 * Removes a [Node] with a certain [NodeId].
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 * > rm node 1 /* node id */
 * 16:52:10.377 [INFO] RM_NODE - node successfully removed
 */
internal data object RmNode : PGCmd("RM_NODE") {
    override val regex = Regex("\\s*rm\\s+(?:n|node)\\s+(\\d+)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val customNetwork: CustomNetwork =
            (coroutineContext[PGEnv]!!.network as? CustomNetwork)
                ?: return cancelAfter { log.error("removing a node is not allowed unless the network is custom type") }

        val nodeId: NodeId = fromStrElseCanc(result.groupValues[1])

        launch {
            async { customNetwork.rmNode(nodeId) }.await()
                ?.let { log.info("node successfully removed") }
                ?: log.error("unable to remove node.")
        }
    }
}
