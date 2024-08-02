package org.opendc.simulator.network.energy.emodels

import org.opendc.simulator.network.components.Switch
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.energy.EnModel
import org.opendc.simulator.network.units.DataRate
import org.opendc.simulator.network.units.Mbps
import org.opendc.simulator.network.units.Watts
import org.opendc.simulator.network.units.times
import kotlin.math.log
import kotlin.math.pow


/**
 * Model defined by Xiaodong Wang et al. in 'CARPO:
 * Correlation-Aware Power Optimization in Data Center Networks'
 *
 * Only derived by 1000Mbps 48 ports PRONTO 3240 switches.
 */
internal object SwitchDfltEnModel: EnModel<Switch> {
    private val CHASSIS_PWR = Watts(67.7)
    private val IDLE_PORT_PWR_10Mbps = Watts(3.0 / 48)
    private val IDLE_PORT_PWR_100Mbps = Watts(12.5 / 48)
    private val IDLE_PORT_PWR_1000Mbps = Watts(43.8 / 48)

    /**
     * The dynamic power usage of the port is equal to its utilization * 5% * static power consumption.
     */
    private const val STATIC_TO_DYNAMIC_RATIO: Double = 0.05

    override fun computeCurrConsumpt(e: Switch): Watts {
        val idlePortPwr: Watts = getPortIdlePwr(e.portSpeed)
        // TODO: change to use port current speed, that in the future could be different from its max speed.
        //      The switch can lower its port speed to save energy.
        val numOfActivePorts: Int = e.getActivePorts().size

        val totalStaticPwr: Watts = CHASSIS_PWR + idlePortPwr * numOfActivePorts
        val totalDynamicPwr: Watts = e.avrgPortUtilization() * STATIC_TO_DYNAMIC_RATIO * totalStaticPwr

        return totalStaticPwr + totalDynamicPwr
    }

    /**
     * Returns static power consumption of a single active port of speed [portSpeed].
     * @param[portSpeed]    speed of the port to compute static energy consumption of.
     */
    private fun getPortIdlePwr(portSpeed: DataRate): Watts {

        return when (portSpeed.toMbps()) {
            Mbps(10.0) -> IDLE_PORT_PWR_10Mbps
            Mbps(100.0) -> IDLE_PORT_PWR_100Mbps
            Mbps(1000.0) -> IDLE_PORT_PWR_1000Mbps
            else -> {
                /**
                 * Just approximated poorly from the 3 constants.
                 * TODO: change
                 */
                Watts(4.0.pow(log(portSpeed.mBpsValue(), 10.0) - 1) * (3.0 / 48.0))
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
