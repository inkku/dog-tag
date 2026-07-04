package com.dogtag.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val FENCE_ALERT_CHANNEL_ID = "fence_alerts"
const val FOREGROUND_SERVICE_CHANNEL_ID = "fence_monitoring"

fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            FENCE_ALERT_CHANNEL_ID,
            "Fence alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "A dog left or is approaching a virtual fence boundary" }
    )
    manager.createNotificationChannel(
        NotificationChannel(
            FOREGROUND_SERVICE_CHANNEL_ID,
            "Fence monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Ongoing BLE scanning for virtual fence monitoring" }
    )
}

fun showFenceAlert(context: Context, notificationId: Int, title: String, message: String) {
    val notification = NotificationCompat.Builder(context, FENCE_ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}
