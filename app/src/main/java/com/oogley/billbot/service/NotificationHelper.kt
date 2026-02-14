package com.oogley.billbot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.oogley.billbot.MainActivity
import com.oogley.billbot.R

object NotificationHelper {

    const val CHANNEL_CHAT = "billbot_chat"
    const val CHANNEL_ALERTS = "billbot_alerts"
    const val CHANNEL_SERVICE = "billbot_service"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHAT, "Chat Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for new chat messages"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Service Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for service health changes"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Background Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while connected"
            }
        )
    }

    fun buildChatNotification(context: Context, sender: String, preview: String, notificationId: Int): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(sender)
            .setContentText(preview.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview.take(500)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("billbot_chat")
            .build()
    }

    fun buildAlertNotification(context: Context, title: String, message: String, notificationId: Int): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    fun buildServiceNotification(context: Context): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BillBot Connected")
            .setContentText("Listening for messages")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
