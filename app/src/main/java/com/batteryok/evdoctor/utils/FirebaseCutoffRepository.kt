package com.batteryok.evdoctor.utils

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class FirebaseCutoffRepository {

    data class Cutoff(val lower: Double, val upper: Double)

    private val cache = ConcurrentHashMap<String, Cutoff>()

    fun getCutoff(chemistry: String, selectedVoltage: Double): Cutoff? {
        val chemistryKey = chemistry.trim().uppercase()
        val voltageKey = toFirebaseVoltageKey(selectedVoltage)
        val cacheKey = "$chemistryKey/$voltageKey"

        cache[cacheKey]?.let { return it }

        val url =
            "https://appinventorlogicdata-default-rtdb.firebaseio.com/cutoff_lookup/$chemistryKey/$voltageKey.json"

        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            connection.inputStream.bufferedReader().use { reader ->
                val payload = reader.readText()
                val json = JSONObject(payload)
                val lower = json.optDouble("lower_cutoff", Double.NaN)
                val upper = json.optDouble("upper_cutoff", Double.NaN)
                if (lower.isNaN() || upper.isNaN()) return@runCatching null
                Cutoff(lower, upper).also { cache[cacheKey] = it }
            }
        }.onFailure {
            Log.w("EVDoctorSoc", "Unable to fetch cutoff from Firebase", it)
        }.getOrNull()
    }

    private fun toFirebaseVoltageKey(voltage: Double): String {
        val normalized = if (voltage % 1.0 == 0.0) {
            voltage.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", voltage)
        }
        return normalized.replace('.', '_')
    }
}
