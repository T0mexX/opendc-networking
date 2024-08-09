package org.opendc.simulator.network.export

import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.InstantSource

/**
 * Contains information about when, where to export [T] and with which fields.
 * This class does not export fields automatically but rather encapsulate the information needed to do so.
 *
 * If [instantSource] is defined, it provides runtime consistency check,
 * verifying that the instant corresponds to the next deadline when [export] is invoked.
 *
 * @property[startInstant]  the instant from which to start exporting.
 * @property[interval]      the interval between 2 exports.
 * @property[exporter]      the exporter to use (the fields included in the output are defined in the exporter).
 * @property[instantSource] used for consistency checks at runtime.
 *
 */
public data class ExportSettings<T: Exportable<T>>(
    public val startInstant: Instant,
    public val interval: Duration,
    private val exporter: Exporter<T>,
    private val instantSource: InstantSource? = null
): Comparable<ExportSettings<*>> {

    public companion object {
        public inline operator fun <reified T: Exportable<T>> invoke(
            startInstant: Instant,
            interval: Duration,
            instantSource: InstantSource? = null,
            outputFile: File,
            vararg fields: ExportField<T>,
        ): ExportSettings<T> =
            ExportSettings(
                startInstant = startInstant,
                interval = interval,
                instantSource = instantSource,
                exporter = Exporter(outputFile = outputFile, fields = fields)
            )
    }

    /**
     * The next instant [export] should be called.
     */
    public var nextDeadline: Instant = startInstant
        private set

    /**
     * Writes to file the exportables retrieved with [getExportables].
     * If [instantSource] is defined, then this function also checks consistency with the
     * time-source (checks the current instant corresponds with the next deadline).
     */
    public fun export(vararg exportables: T) {
        // If the instant source is defined, then check that the current instant corresponds to the deadline.
        instantSource?.let { check(nextDeadline == instantSource.instant()) }

        // Retrieve the exportables and write them.
        exportables.forEach { exporter.write(it) }

        // Update the next deadline.
        nextDeadline += interval
    }

    /**
     * Skips [n] exports.
     */
    public fun skip(n: Int) {
        repeat(n) { nextDeadline += interval }
    }

    /**
     * Orders export settings based on the next deadline.
     */
    override fun compareTo(other: ExportSettings<*>): Int =
        this.nextDeadline.compareTo(other.nextDeadline)
}
