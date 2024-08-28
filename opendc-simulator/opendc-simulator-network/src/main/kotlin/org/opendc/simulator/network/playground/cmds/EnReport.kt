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
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.utils.infoNewLn

/**
 * Logs the energy report of the network.
 * Check [regex] for a complete understanding of the command parsing.
 *
 * ```console
 * // Example
 * > energy report
 * 16:04:47.178 [INFO] ENERGY_REPORT -
 * | ==== Energy Report ====
 * | Current Power Usage: 135.400000 Watts
 * | Total Energy Consumed: 1354.000000 Joule
 * ```
 */
internal data object EnReport : PGCmd("ENERGY_REPORT") {
    override val regex = Regex("\\s*(?:energy|en|e)(?:|report|rep|r)\\s*")

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val pgEnv: PGEnv = coroutineContext[PGEnv]!!

        launch {
            pgEnv.network.awaitStability()
            log.infoNewLn(pgEnv.energyRecorder.fmt())
        }
    }
}
