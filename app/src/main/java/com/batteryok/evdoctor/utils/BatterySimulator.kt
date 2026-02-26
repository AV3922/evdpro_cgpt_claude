package com.batteryok.evdoctor.utils

import com.batteryok.evdoctor.model.BatteryFault
import com.batteryok.evdoctor.model.BatteryReading
import com.batteryok.evdoctor.model.BatteryReport
import com.batteryok.evdoctor.model.FaultSeverity
import com.batteryok.evdoctor.model.SafetyStatus
import com.batteryok.evdoctor.model.TestSession
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Simulates real-time battery data for demo/test purposes.
 * In production, this would be replaced by actual BMS/Bluetooth data.
 */
object BatterySimulator {

    private var tick = 0
    private var baseVoltage = 48.0
    private var baseSoc = 85.0

    fun reset(nominalVoltage: Double, initialSoc: Double) {
        tick = 0
        baseVoltage = nominalVoltage
        baseSoc = min(100.0, max(0.0, initialSoc))
    }

    fun nextReading(nominalVoltage: Double, nominalCapacity: Double): BatteryReading {
        tick++

        // Simulate gradual discharge
        val socDecay = 0.01 * tick
        val soc = max(0.0, baseSoc - socDecay + Random.nextDouble(-0.5, 0.5))

        val voltageRatio = soc / 100.0
        val voltage = nominalVoltage * (0.85 + voltageRatio * 0.15) + Random.nextDouble(-0.3, 0.3)

        val current = Random.nextDouble(2.0, 15.0)
        val temperature = 28.0 + Random.nextDouble(-2.0, 5.0)
        val power = voltage * current
        val capacity = nominalCapacity * (soc / 100.0)
        val internalResistance = 15.0 + Random.nextDouble(-2.0, 8.0)
        val healthScore = max(60.0, 95.0 - (tick * 0.05))
        val cycleCount = 120 + tick / 30

        return BatteryReading(
            timestamp = System.currentTimeMillis(),
            voltage = String.format("%.2f", voltage).toDouble(),
            current = String.format("%.2f", current).toDouble(),
            soc = String.format("%.1f", soc).toDouble(),
            capacity = String.format("%.2f", capacity).toDouble(),
            temperature = String.format("%.1f", temperature).toDouble(),
            power = String.format("%.1f", power).toDouble(),
            internalResistance = String.format("%.1f", internalResistance).toDouble(),
            cycleCount = cycleCount,
            healthScore = String.format("%.1f", healthScore).toDouble()
        )
    }

    fun generateReport(
        session: TestSession,
        readings: List<BatteryReading>,
        durationMs: Long
    ): BatteryReport {
        val finalReading = readings.lastOrNull() ?: BatteryReading()
        val avgHealth = readings.map { it.healthScore }.average()

        val safetyStatus = when {
            finalReading.temperature > 45 || finalReading.voltage < 40 -> SafetyStatus.CRITICAL
            finalReading.temperature > 38 || finalReading.soc < 15 -> SafetyStatus.WARNING
            else -> SafetyStatus.SAFE
        }

        val faults = buildList {
            if (finalReading.temperature > 45) {
                add(BatteryFault("OT001", "Overtemperature detected", FaultSeverity.HIGH))
            }
            if (finalReading.internalResistance > 20) {
                add(BatteryFault("IR001", "High internal resistance", FaultSeverity.MEDIUM))
            }
            if (finalReading.soc < 20) {
                add(BatteryFault("SOC001", "Low state of charge", FaultSeverity.LOW))
            }
        }

        return BatteryReport(
            session = session,
            readings = readings,
            finalReading = finalReading,
            healthScore = avgHealth,
            safetyStatus = safetyStatus,
            faults = faults,
            endTime = System.currentTimeMillis(),
            testDuration = durationMs
        )
    }
}
