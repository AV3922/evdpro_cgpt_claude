package com.batteryok.evdoctor.model

data class BatterySample(
    val soc: Int,
    val voltage: Double,
    val current: Double,
    val capacity: Double,
    val hour: Int,
    val minute: Int,
    val second: Int
)
