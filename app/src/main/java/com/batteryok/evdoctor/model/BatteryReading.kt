package com.batteryok.evdoctor.model

import java.io.Serializable

data class BatteryReading(
    val timestamp: Long = System.currentTimeMillis(),
    val voltage: Double = 0.0,         // Volts
    val current: Double = 0.0,         // Amperes
    val soc: Double = 0.0,             // State of Charge (%)
    val capacity: Double = 0.0,        // Current capacity (Ah)
    val temperature: Double = 0.0,     // Celsius
    val power: Double = 0.0,           // Watts
    val internalResistance: Double = 0.0, // milli-Ohms
    val cycleCount: Int = 0,
    val healthScore: Double = 0.0      // Battery health (%)
) : Serializable

data class BatteryReport(
    val session: TestSession = TestSession(),
    val readings: List<BatteryReading> = emptyList(),
    val finalReading: BatteryReading = BatteryReading(),
    val healthScore: Double = 0.0,
    val safetyStatus: SafetyStatus = SafetyStatus.SAFE,
    val faults: List<BatteryFault> = emptyList(),
    val endTime: Long = System.currentTimeMillis(),
    val testDuration: Long = 0L
) : Serializable

enum class SafetyStatus : Serializable {
    SAFE, WARNING, CRITICAL
}

data class BatteryFault(
    val code: String,
    val description: String,
    val severity: FaultSeverity
) : Serializable

enum class FaultSeverity : Serializable {
    LOW, MEDIUM, HIGH, CRITICAL
}
