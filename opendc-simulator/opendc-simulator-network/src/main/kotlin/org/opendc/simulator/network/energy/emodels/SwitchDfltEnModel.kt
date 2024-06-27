package org.opendc.simulator.network.energy.emodels

import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Mbps
import org.opendc.simulator.network.utils.Watts
import org.opendc.simulator.network.utils.toHigherDataUnit
import kotlin.math.log
import kotlin.math.pow


/**
 * Model defined by Xiaodong Wang et al. in 'CARPO:
 * Correlation-Aware Power Optimization in Data Center Networks'
 */
internal object SwitchDfltEnModel: EnModel<Switch> {
    private const val CHASSIS_PWR: Watts = 66.7
    private const val IDLE_PORT_PWR_10Mbps: Watts = 3.0 / 48
    private const val IDLE_PORT_PWR_100Mbps: Watts = 12.5 / 48
    private const val IDLE_PORT_PWR_1000Mbps: Watts = 43.8 / 48

    /**
     * The dynamic power usage of the port is equal to its utilization * 5% * static power consumption.
     */
    private const val STATIC_TO_DYNAMIC_RATIO: Double = 0.05
    // TODO: linecards? no info on their power usage or number in this publication

    override fun computeCurrConsumpt(enConsumer: Switch): Watts {
        val idlePortPwr: Watts = getPortIdlePwr(enConsumer.portSpeed)
        val numActivePorts: Int = enConsumer.getActivePortNum()

        val totalStaticPwr: Watts = CHASSIS_PWR + idlePortPwr * numActivePorts
        val totalDynamicPwr: Watts = enConsumer.getAvrgPortUtilization() * STATIC_TO_DYNAMIC_RATIO * totalStaticPwr

        return totalStaticPwr + totalDynamicPwr
    }

    /**
     * Returns static power consumption of a single active port of speed [portSpeed].
     * @param[portSpeed]    speed of the port to compute static energy consumption of.
     */
    private fun getPortIdlePwr(portSpeed: Kbps): Watts {
        val portSpeedMbps: Mbps = portSpeed.toHigherDataUnit()
        return when (portSpeedMbps) {
            10.0 -> IDLE_PORT_PWR_10Mbps
            100.0 -> IDLE_PORT_PWR_100Mbps
            1000.0 -> IDLE_PORT_PWR_1000Mbps
            else -> {
                /**
                 * Just approximated poorly from the 3 constants.
                 * TODO: change
                 */
                4.0.pow(log(portSpeed.toDouble(), 10.0)) * 3.0
            }
        }
    }
}
