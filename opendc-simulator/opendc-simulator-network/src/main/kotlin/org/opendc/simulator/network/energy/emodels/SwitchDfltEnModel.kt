package org.opendc.simulator.network.energy.emodels

import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.utils.Kbps
import org.opendc.simulator.network.utils.Mbps
import org.opendc.simulator.network.utils.W
import org.opendc.simulator.network.utils.toHigherDataUnit
import kotlin.math.log
import kotlin.math.pow


/**
 * Model defined by Xiaodong Wang et al. in 'CARPO:
 * Correlation-Aware Power Optimization in Data Center Networks'
 *
 * Only derived by 1000Mbps 48 ports PRONTO 3240 switches.
 */
internal object SwitchDfltEnModel: EnModel<Switch> {
    private const val CHASSIS_PWR: W = 67.7
    private const val IDLE_PORT_PWR_10Mbps: W = 3.0 / 48
    private const val IDLE_PORT_PWR_100Mbps: W = 12.5 / 48
    private const val IDLE_PORT_PWR_1000Mbps: W = 43.8 / 48

    /**
     * The dynamic power usage of the port is equal to its utilization * 5% * static power consumption.
     */
    private const val STATIC_TO_DYNAMIC_RATIO: Double = 0.05

    override fun computeCurrConsumpt(e: Switch): W {
        val idlePortPwr: W = getPortIdlePwr(e.portSpeed)
        // TODO: change to use port current speed, that in the future could be different from its max speed.
        //      The switch can lower its port speed to save energy.
        val numOfActivePorts: Int = e.getActivePorts().size

        val totalStaticPwr: W = CHASSIS_PWR + idlePortPwr * numOfActivePorts
        val totalDynamicPwr: W = e.avrgPortUtilization() * STATIC_TO_DYNAMIC_RATIO * totalStaticPwr

        return totalStaticPwr + totalDynamicPwr
    }

    /**
     * Returns static power consumption of a single active port of speed [portSpeed].
     * @param[portSpeed]    speed of the port to compute static energy consumption of.
     */
    private fun getPortIdlePwr(portSpeed: Kbps): W {
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
                4.0.pow(log(portSpeedMbps, 10.0) - 1) * (3.0 / 48.0)
            }
        }
    }

    /**
     *  @return the ports of ***this*** [Switch] that are currently active.
     *  @see[Port.isActive]
     */
    private fun Switch.getActivePorts(): Collection<Port> =
        this.portToNode.values.filter { it.isActive  }

    /**
     * @return  average port utilization considering both active and not active ports.
     */
    private fun Switch.avrgPortUtilization(): Double =
        this.ports.sumOf { it.util } / ports.size
}
