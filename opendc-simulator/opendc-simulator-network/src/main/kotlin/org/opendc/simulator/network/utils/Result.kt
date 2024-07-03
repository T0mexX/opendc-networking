package org.opendc.simulator.network.utils

internal interface Result {

    object SUCCESS: Result

    class ERROR(val msg: String = ""): Result
}

