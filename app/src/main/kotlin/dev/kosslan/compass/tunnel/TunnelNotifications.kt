package dev.kosslan.compass.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.kosslan.compass.MainActivity
import dev.kosslan.compass.R

object TunnelNotifications {
    const val ID = 1
    private const val CHANNEL = "tunnel"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    fun running(context: Context): Notification {
        val disconnect =
            PendingIntent.getService(
                context,
                0,
                CompassVpnService.disconnectIntent(context),
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.action_disconnect), disconnect)
            .build()
    }
}
