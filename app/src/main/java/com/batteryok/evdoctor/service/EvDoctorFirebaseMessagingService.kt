package com.batteryok.evdoctor.service

import android.util.Log
import com.batteryok.evdoctor.utils.NotificationUtils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class EvDoctorFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("EVDoctorFCM", "FCM token refreshed: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "EV Doctor"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new notification"

        NotificationUtils.showNotification(this, title, body)
    }
}
