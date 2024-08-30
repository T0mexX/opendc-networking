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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.components.Network.Companion.INTERNET_ID
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.utils.logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A command executable from CLI that allows to interact with the network.
 */
internal sealed class PGCmd(val name: String) {
    /**
     * The regex used to match CLI input to playground commands.
     */
    abstract val regex: Regex

    protected abstract fun CoroutineScope.execCmd(result: MatchResult)

    fun CoroutineScope.exec(result: MatchResult): Job = launchNamed { execCmd(result) }

    protected val log by logger(name)

    /**
     * Wrapper for [CoroutineScope.launch] that launches the coroutine with the command [this.name].
     */
    private fun CoroutineScope.launchNamed(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = this.launch(context + CoroutineName(name), block = block)

    protected inline fun <reified T> fromStrElseNull(str: String): T? =
        try {
            json.decodeFromString(str)
        } catch (_: Exception) {
            null
        }

    protected inline fun <reified T> fromStrElseCanc(str: String): T =
        fromStrElseNull(str)
            ?: let {
                val cancExc = CancellationException("unable to parse string '$str' as ${T::class.simpleName}")
                log.error(cancExc.message)
                throw cancExc
            }

    protected fun ifInternetElseNull(str: String): NodeId? =
        if (internetReg.matches(str)) {
            INTERNET_ID
        } else {
            null
        }

    fun Network.getNodeElseCanc(nodeId: NodeId): Node =
        this[nodeId]
            ?: let {
                val cancExc = CancellationException("invalid node id '$nodeId'")
                log.error(cancExc.message)
                throw cancExc
            }

    companion object {
        val json = Json { prettyPrint = true }

        private val internetReg = Regex("(?:\\s*int|i|internet)(?:|\\s+id)\\s*?", RegexOption.IGNORE_CASE)

        fun CoroutineScope.cancelAfter(f: () -> Unit) {
            f.invoke()
            this.cancel()
        }
    }
}
