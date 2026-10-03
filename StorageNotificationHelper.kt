package com.yourapp.storageapp.storage

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourapp.storageapp.MainActivity

object StorageNotificationHelper {

    private const val CHANNEL_ID = "storage_alerts_channel"
    private const val CHANNEL_NAME = "Storage Alerts"
    private const val HIGH_STORAGE_NOTIF_ID = 1001
    private const val JUNK_CLEAN_NOTIF_ID = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts for high storage usage and cleanable junk"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission")
    fun checkAndSendAlerts(context: Context, usedPercentage: Float, junkBytes: Long) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 1. High Storage Warning Alert
        if (usedPercentage >= 85f) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Low Storage Warning")
                .setContentText("Storage is ${String.format(java.util.Locale.getDefault(), "%.1f", usedPercentage)}% full. Tap to clean up space.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                NotificationManagerCompat.from(context).notify(HIGH_STORAGE_NOTIF_ID, builder.build())
            } catch (_: Exception) {
                // Ignore missing POST_NOTIFICATIONS permission
            }
        }

        // 2. Junk Cleaning Suggestion Alert
        if (junkBytes > (200 * 1024 * 1024)) { // > 200 MB junk
            val formattedJunk = formatBytes(junkBytes)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Junk Files Found")
                .setContentText("Reclaim $formattedJunk of disposable cache and temp files.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                NotificationManagerCompat.from(context).notify(JUNK_CLEAN_NOTIF_ID, builder.build())
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}
