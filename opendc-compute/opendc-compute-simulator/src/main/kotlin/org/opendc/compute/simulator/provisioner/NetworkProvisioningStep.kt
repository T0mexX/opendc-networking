package org.opendc.compute.simulator.provisioner

import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.ALL
import org.opendc.simulator.network.api.NetworkSnapshot.Companion.snapshot

public class NetworkProvisioningStep(
    private val netController: NetworkController?
): ProvisioningStep {
    override fun apply(ctx: ProvisioningContext): AutoCloseable {
        if (netController == null) return AutoCloseable {  }

        // Sets an external time source for the network simulation,
        // which can be advanced up to the instant source instant with sync()
        netController.setInstantSource(ctx.dispatcher.timeSource)

        // Prints the status of the network.
        println(netController.snapshot().fmt(ALL))

        return netController
    }
}
