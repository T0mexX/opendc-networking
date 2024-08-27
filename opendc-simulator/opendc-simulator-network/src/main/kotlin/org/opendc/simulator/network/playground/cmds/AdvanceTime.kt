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
import org.opendc.common.units.Time
import org.opendc.simulator.network.api.NetEnRecorder
import org.opendc.simulator.network.components.Network
import org.opendc.simulator.network.playground.PGEnv
import org.opendc.simulator.network.playground.PGTimeSource

/**
 * Advances the playground time, updating time-dependent information.
 *
 * ```console
 * // Example
 * > advance by 10sec
 * 16:43:43.607 [INFO] ADVANCE_TIME - advanced time by PT10S. Time elapsed since start: PT10S
 * ```
 */
internal data object AdvanceTime : PGCmd("ADVANCE_TIME") {
    override val regex = Regex("\\s*(?:advance by|advance|adv)\\s+(.+)\\s*", RegexOption.IGNORE_CASE)

    override fun CoroutineScope.execCmd(result: MatchResult) {
        val pgEnv: PGEnv = coroutineContext[PGEnv]!!
        val network: Network = pgEnv.network
        val enRecorder: NetEnRecorder = pgEnv.energyRecorder
        val pgTimeSource: PGTimeSource = pgEnv.pgTimeSource

        val timeDelta: Time = json.decodeFromString(result.groupValues[1])

        launch {
            network.awaitStability()
            pgTimeSource.advanceBy(timeDelta)
            network.advanceBy(timeDelta)
            enRecorder.advanceBy(timeDelta)

            log.info("advanced time by $timeDelta. Time elapsed since start: ${pgTimeSource.timeElapsed}")
        }
    }
}
