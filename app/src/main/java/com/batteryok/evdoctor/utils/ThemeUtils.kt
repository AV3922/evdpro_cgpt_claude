package com.batteryok.evdoctor.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {

    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "night_mode"

    fun applyTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun saveTheme(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, mode)
            .apply()
    }

    fun isDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d : %02d : %02d", hours, minutes, seconds)
    }
}
