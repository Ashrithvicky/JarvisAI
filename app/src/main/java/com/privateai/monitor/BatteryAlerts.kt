package com.privateai.monitor

/** Decides when a battery level change deserves a spoken alert. */
object BatteryAlerts {
    const val LOW = 30
    const val HIGH = 80

    fun check(previous: Int, level: Int, charging: Boolean): String? = when {
        previous < 0 || previous == level -> null
        !charging && previous > LOW && level <= LOW ->
            "Boss, mobile's battery is at $level percent. Time to charge."
        charging && previous < HIGH && level >= HIGH ->
            "Boss, mobile's battery has reached $level percent. You can unplug the charger."
        else -> null
    }
}
