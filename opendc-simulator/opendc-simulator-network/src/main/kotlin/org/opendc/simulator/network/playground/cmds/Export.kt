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
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.playground.PGEnv
import java.io.File
import java.io.IOException
import kotlin.io.path.createParentDirectories

internal data object Export : PGCmd("EXPORT") {
    override val regex = Regex("\\s*(?:export|exp)\\s+(.+)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val network: Network = (coroutineContext[PGEnv]!!.network)

        val targetFile = File(result.groupValues[1])

        try {
            if (targetFile.exists().not()) {
                targetFile.toPath().normalize().createParentDirectories()
            }

            targetFile.writeText(json.encodeToString(network))
        } catch (e: IOException) {
            val cancExc =
                CancellationException(
                    message = "unable to export network",
                    cause = e,
                )
            log.error("${cancExc.message}. Reason: ${cancExc.cause?.message ?: "unknown"}")
            throw cancExc
        }

        log.info("network successfully exported to ${targetFile.absolutePath}")
    }
}
