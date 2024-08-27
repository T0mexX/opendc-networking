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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.flow.FlowId
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.cmds.NodeInfo.regex

/**
 * Stops a network flow with certain [FlowId].
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 * > rm flow 1 /* flow id */
 * 16:49:13.409 [INFO] RM_FLOW - flow successfully stopped
 */
internal data object RmFlow : PGCmd("RM_FLOW") {
    override val regex = Regex("\\s*rm\\s+(?:f|flow)\\s+(\\d+)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val network: Network = (coroutineContext[PGEnv]!!.network)

        val flowId: FlowId = fromStrElseCanc(result.groupValues[1])

        launch {
            async { network.stopFlow(flowId) }.await()
                ?.let { log.info("flow successfully stopped") }
                ?: log.error("unable to stop flow")
        }
    }
}
