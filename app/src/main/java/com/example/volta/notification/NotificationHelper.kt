package com.example.volta.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.volta.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS_ID = "volta_alerts_channel"
        const val CHANNEL_HANDSHAKE_ID = "volta_handshake_channel"
        const val CHANNEL_TIMERS_ID = "volta_timers_channel"
        const val HANDSHAKE_NOTIFICATION_ID = 2001
        const val ALERT_NOTIFICATION_ID = 3001
        const val TIMER_NOTIFICATION_ID = 4001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Volta Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Power strip status, voltage, temperature, and outage alerts"
                enableVibration(true)
            }

            val handshakeChannel = NotificationChannel(
                CHANNEL_HANDSHAKE_ID,
                "Volta Pairing & Handshake",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for MTTL strip pairing and account claim results"
                enableVibration(true)
            }

            val timersChannel = NotificationChannel(
                CHANNEL_TIMERS_ID,
                "Volta Timers & Automations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when timers or automation schedules switch outlets"
                enableVibration(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(handshakeChannel)
            manager.createNotificationChannel(timersChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showHandshakeNotification(success: Boolean, mac: String, details: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (success) "⚡ Strip Handshake Succeeded" else "⚠️ Strip Handshake Failed"
        val cleanMac = if (mac.isNotBlank()) "Strip ${mac.takeLast(6).uppercase()}" else "MTTL Strip"
        val contentText = if (success) "$cleanMac provisioned and claimed to your account." else details

        val builder = NotificationCompat.Builder(context, CHANNEL_HANDSHAKE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n\n$details"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(HANDSHAKE_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {}
    }

    fun showAlertNotification(title: String, message: String, notificationId: Int = ALERT_NOTIFICATION_ID) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    fun showTimerNotification(title: String, message: String, notificationId: Int = (System.currentTimeMillis() % 10000).toInt() + 4000) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_TIMERS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }
}
