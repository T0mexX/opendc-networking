package org.opendc.compute.simulator.provisioner

import org.opendc.common.logger.infoNewLine
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.NetworkController
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.INSTANT
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.NODES
import org.opendc.simulator.network.api.snapshots.NetworkSnapshot.Companion.snapshot
import org.opendc.simulator.network.export.NetworkExportConfig
import java.io.File

public class NetworkProvisioningStep(
    private val netController: NetworkController? = null,
    private val seedOutputFolder: File,
    netExportConfig: NetworkExportConfig? = null,
): ProvisioningStep {
    private val netExportConfig: NetworkExportConfig? =
        netExportConfig?.let { config ->
            println(seedOutputFolder)
            config.outputFolder?.let {
                println(it)
                config
            } ?: config.copy(outputFolder = seedOutputFolder)
        }

    override fun apply(ctx: ProvisioningContext): AutoCloseable {
        netController?.let {
            // Sets an external time source for the network simulation,
            // which can be advanced up to the instant source instant with sync()
            netController.setInstantSource(ctx.dispatcher.timeSource)

            // Prints the status of the network.
            LOG.infoNewLine(
                netController.snapshot().fmt(
                    flags =
                        INSTANT +
                        NODES
                )
            )

            // Setup network exporting according to netExportConfig.
            netExportConfig?.let {
                netController.setExportConfig(
                    netExportConfig
                )

                // Logs the setting used for network exporting.
                LOG.infoNewLine(netExportConfig.fmt())
            }


        } ?: return AutoCloseable {  }



        return netController
    }

    private companion object {
        val LOG by logger()
    }
}
