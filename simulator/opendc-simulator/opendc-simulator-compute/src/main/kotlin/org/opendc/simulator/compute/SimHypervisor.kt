/*
 * Copyright (c) 2020 AtLarge Research
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

package org.opendc.simulator.compute

import org.opendc.simulator.compute.interference.PerformanceInterferenceModel
import org.opendc.simulator.compute.workload.SimWorkload

/**
 * SimHypervisor distributes the computing requirements of multiple [SimWorkload] on a single [SimBareMetalMachine] i
 * concurrently.
 */
public interface SimHypervisor : SimWorkload {
    /**
     * Create a [SimMachine] instance on which users may run a [SimWorkload].
     *
     * @param model The machine to create.
     */
    public fun createMachine(
        model: SimMachineModel,
        performanceInterferenceModel: PerformanceInterferenceModel? = null
    ): SimMachine

    /**
     * Event listener for hypervisor events.
     */
    public interface Listener {
        /**
         * This method is invoked when a slice is finished.
         */
        public fun onSliceFinish(
            hypervisor: SimHypervisor,
            requestedBurst: Long,
            grantedBurst: Long,
            overcommissionedBurst: Long,
            interferedBurst: Long,
            cpuUsage: Double,
            cpuDemand: Double
        )
    }
}
