package com.batteryok.evdoctor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.batteryok.evdoctor.R
import kotlin.random.Random

object NotificationUtils {
    const val CHANNEL_TEST_ALERTS = "evdoctor_test_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_TEST_ALERTS,
            "EV Doctor Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Test completion and cloud message alerts"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, title: String, body: String) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_TEST_ALERTS)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(Random.nextInt(), notification)
    }
}
