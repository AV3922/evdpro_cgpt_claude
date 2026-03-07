package com.batteryok.evdoctor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.batteryok.evdoctor.R
import java.util.Locale

class TestForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "evdoctor_test_running"
        private const val NOTIFICATION_ID = 1107

        private const val ACTION_START = "com.batteryok.evdoctor.action.START_TEST_SERVICE"
        private const val ACTION_UPDATE = "com.batteryok.evdoctor.action.UPDATE_TEST_SERVICE"
        private const val ACTION_STOP = "com.batteryok.evdoctor.action.STOP_TEST_SERVICE"

        private const val EXTRA_VOLTAGE = "extra_voltage"
        private const val EXTRA_CURRENT = "extra_current"
        private const val EXTRA_ELAPSED_MS = "extra_elapsed_ms"

        fun start(context: Context) {
            val intent = Intent(context, TestForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, voltage: Double, current: Double, elapsedMs: Long) {
            val intent = Intent(context, TestForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_VOLTAGE, voltage)
                putExtra(EXTRA_CURRENT, current)
                putExtra(EXTRA_ELAPSED_MS, elapsedMs)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TestForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var lastVoltage = 0.0
    private var lastCurrent = 0.0
    private var lastElapsedMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        when (intent?.action) {
            ACTION_START -> startForeground(NOTIFICATION_ID, buildNotification())
            ACTION_UPDATE -> {
                lastVoltage = intent.getDoubleExtra(EXTRA_VOLTAGE, lastVoltage)
                lastCurrent = intent.getDoubleExtra(EXTRA_CURRENT, lastCurrent)
                lastElapsedMs = intent.getLongExtra(EXTRA_ELAPSED_MS, lastElapsedMs)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val elapsedMinutes = (lastElapsedMs / 60000L).coerceAtLeast(0)
        val content = String.format(
            Locale.US,
            "Voltage: %.1f V  Current: %.1f A  Time Elapsed: %d minutes",
            lastVoltage,
            lastCurrent,
            elapsedMinutes
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle("EV Doctor Test Running")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "EV Doctor Running Test",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground notification while battery test is running"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
