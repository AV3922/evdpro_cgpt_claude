package com.batteryok.evdoctor.model

import java.io.Serializable

data class TestSession(
    val clientInfo: ClientInfo = ClientInfo(),
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val testMode: String = "NORMAL",
    val startTime: Long = System.currentTimeMillis()
) : Serializable
