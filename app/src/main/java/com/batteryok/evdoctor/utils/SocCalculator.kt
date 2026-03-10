package com.batteryok.evdoctor.utils

import com.batteryok.evdoctor.model.BatterySample
import com.batteryok.evdoctor.model.TestSession
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class SocCalculator(
    private val chemistry: String,
    private val ratedVoltage: Double,
    private val firebaseLowerCutoff: Double?,
    private val firebaseUpperCutoff: Double?
) {

    private var previousVoltage: Double? = null
    private var previousCurrent: Double? = null

    fun calculate(
        voltageRead: Double,
        current: Double,
        capacity: Double,
        hour: Int,
        minute: Int,
        second: Int
    ): BatterySample {
        val chemistryNorm = chemistry.trim().uppercase()
        val soc = if (chemistryNorm == "VRLA") {
            vrlaSoc(voltageRead, ratedVoltage)
        } else {
            val packSoc = packVoltageSoc(voltageRead, chemistryNorm, ratedVoltage)
            val firebaseSoc = firebaseSoc(voltageRead)
            val dvdiSoc = dvdiFactor(voltageRead, current)
            val finalSoc = 0.8 * firebaseSoc + 0.2 * packSoc + 0.2 * dvdiSoc
            clampPercent(finalSoc)
        }

        return BatterySample(
            soc = floor(soc).toInt(),
            voltage = voltageRead,
            current = current,
            capacity = capacity,
            hour = hour,
            minute = minute,
            second = second
        )
    }

    private fun packVoltageSoc(voltageRead: Double, chemistryNorm: String, batteryVoltage: Double): Double {
        val cellBase = when (chemistryNorm) {
            "LFP" -> 3.2
            else -> 3.7 // NMC / LMFP
        }

        val cellCount = max(1.0, batteryVoltage / cellBase)

        val (upperCellVoltage, lowerCellVoltage) = when (chemistryNorm) {
            "LFP" -> 3.6 to 2.65
            else -> 4.2 to 3.0
        }

        val socCellPackUpper = (upperCellVoltage * cellCount) + 0.05
        val socCellPackLower = (lowerCellVoltage * cellCount) - 0.05

        val denominator = socCellPackUpper - socCellPackLower
        if (denominator == 0.0) return 0.0

        val soc = ((voltageRead - socCellPackLower) / denominator) * 100.0
        return clampPercent(soc)
    }

    private fun firebaseSoc(voltageRead: Double): Double {
        val lower = firebaseLowerCutoff
        val upper = firebaseUpperCutoff

        if (lower == null || upper == null || upper == lower) {
            return 0.0
        }

        val soc = ((voltageRead - lower) / (upper - lower)) * 100.0
        return clampPercent(soc)
    }

    private fun dvdiFactor(currentVoltage: Double, currentValue: Double): Double {
        val prevV = previousVoltage
        val prevI = previousCurrent

        previousVoltage = currentVoltage
        previousCurrent = currentValue

        if (prevV == null || prevI == null) return 0.0

        val socDv = currentVoltage - prevV
        val socDi = currentValue - prevI

        if (socDi == 0.0) return 0.0

        val response = socDv / socDi
        return min(1.0, response)
    }

    private fun vrlaSoc(voltageRead: Double, batteryVoltage: Double): Double {
        val soc = when {
            batteryVoltage <= 2.2 -> (voltageRead - 1.8) * 100.0 / (2.6 - 1.8)
            else -> (voltageRead - 10.5) * 100.0 / (14.7 - 10.5)
        }
        return clampPercent(soc)
    }

    private fun clampPercent(value: Double): Double {
        return max(0.0, min(100.0, value))
    }

    companion object {
        fun fromSession(session: TestSession, lowerCutoff: Double?, upperCutoff: Double?): SocCalculator {
            val chemistry = session.batteryInfo.chemistry.ifBlank { session.batteryInfo.make }
            return SocCalculator(
                chemistry = chemistry,
                ratedVoltage = session.batteryInfo.nominalVoltage,
                firebaseLowerCutoff = lowerCutoff,
                firebaseUpperCutoff = upperCutoff
            )
        }
    }
}
