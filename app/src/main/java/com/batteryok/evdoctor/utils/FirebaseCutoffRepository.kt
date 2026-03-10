package com.batteryok.evdoctor.utils

import android.util.Log
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class FirebaseCutoffRepository {

    data class Cutoff(val lower: Double, val upper: Double)

    private val cache = ConcurrentHashMap<String, Cutoff>()

    fun getCutoff(chemistry: String, selectedVoltage: Double): Cutoff? {
        val chemistryCandidates = chemistryCandidates(chemistry)
        val voltageCandidates = voltageKeyCandidates(selectedVoltage)

        for (chemistryKey in chemistryCandidates) {
            for (voltageKey in voltageCandidates) {
                val cacheKey = "$chemistryKey/$voltageKey"
                cache[cacheKey]?.let { return it }

                val url =
                    "https://appinventorlogicdata-default-rtdb.firebaseio.com/cutoff_lookup/$chemistryKey/$voltageKey.json"

                val cutoff = fetchCutoff(url)
                if (cutoff != null) {
                    cache[cacheKey] = cutoff
                    return cutoff
                }
            }
        }

        return null
    }

    private fun fetchCutoff(url: String): Cutoff? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            connection.inputStream.bufferedReader().use { reader ->
                val payload = reader.readText()
                val jsonAny = JSONTokener(payload).nextValue()
                val json = jsonAny as? JSONObject ?: return@runCatching null
                val lower = json.optDouble("lower_cutoff", Double.NaN)
                val upper = json.optDouble("upper_cutoff", Double.NaN)
                if (lower.isNaN() || upper.isNaN()) return@runCatching null
                Cutoff(lower, upper)
            }
        }.onFailure {
            Log.w("EVDoctorSoc", "Unable to fetch cutoff from Firebase", it)
        }.getOrNull()
    }

    private fun chemistryCandidates(rawChemistry: String): List<String> {
        val normalized = rawChemistry.trim().uppercase(Locale.US)
        if (normalized.isBlank()) return emptyList()

        val alias = when {
            normalized.contains("LIFEPO") || normalized.contains("LFP") -> "LFP"
            normalized.contains("LMFP") -> "LMFP"
            normalized.contains("NMC") -> "NMC"
            normalized.contains("VRLA") || normalized.contains("LEAD") -> "VRLA"
            else -> normalized
        }

        return listOf(alias, normalized).distinct()
    }

    private fun voltageKeyCandidates(voltage: Double): List<String> {
        if (!voltage.isFinite() || voltage <= 0.0) return emptyList()

        val roundedInt = voltage.toInt()
        val intKey = roundedInt.toString()
        val oneDecimal = String.format(Locale.US, "%.1f", voltage)
        val oneDecimalUnderscore = oneDecimal.replace('.', '_')

        return listOf(intKey, oneDecimalUnderscore).distinct()
    }
}
