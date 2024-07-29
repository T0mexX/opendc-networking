package org.opendc.simulator.network.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase


public class LogBackCustomColor : ForegroundCompositeConverterBase<ILoggingEvent>() {
    protected override fun getForegroundColorCode(event: ILoggingEvent): String {
        val level: Level = event.level
        return when (level.toInt()) {
            Level.ERROR_INT -> ANSIConstants.BOLD + ANSIConstants.RED_FG // same as default color scheme
            Level.WARN_INT -> ANSIConstants.BOLD + ANSIConstants.YELLOW_FG
            Level.INFO_INT -> ANSIConstants.BOLD + ANSIConstants.GREEN_FG
            else -> ANSIConstants.DEFAULT_FG
        }
    }
}
