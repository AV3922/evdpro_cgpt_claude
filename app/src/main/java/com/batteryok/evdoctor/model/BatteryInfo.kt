package com.batteryok.evdoctor.model

import java.io.Serializable

data class BatteryInfo(
    val vehicleModel: String = "",
    val vehicleNumber: String = "",
    val make: String = "",
    val model: String = "",
    val chemistry: String = "",
    val nominalCapacity: Double = 0.0,
    val nominalVoltage: Double = 0.0,
    val cellCount: Int = 0
) : Serializable
