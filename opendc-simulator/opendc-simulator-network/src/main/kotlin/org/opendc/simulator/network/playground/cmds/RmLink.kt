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
import org.opendc.simulator.network.components.disconnect
import org.opendc.simulator.network.playground.PGEnv

@OptIn(ExperimentalCoroutinesApi::class)
internal data object RmLink : PGCmd("RM_LINK") {
    override val regex = Regex("\\s*rm\\s+(?:l|link)\\s+(\\d+)\\s*[- ]\\s*(\\d+)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val customNetwork: CustomNetwork =
            (coroutineContext[PGEnv]!!.network as? CustomNetwork)
                ?: return cancelAfter { log.error("connecting 2 nodesById is not allowed unless the network is custom type") }

        val node1Id: NodeId = fromStrElseCanc(result.groupValues[1])
        val node2Id: NodeId = fromStrElseCanc(result.groupValues[2])

        val node1: Node = customNetwork.getNodeElseCanc(node1Id)
        val node2: Node = customNetwork.getNodeElseCanc(node2Id)

        launch {
            async { node1.disconnect(node2) }.await()
                .let { success ->
                    if (success) {
                        log.info("link successfully removed")
                    } else {
                        log.error("unable to remove link.")
                    }
                }
        }
    }
}
