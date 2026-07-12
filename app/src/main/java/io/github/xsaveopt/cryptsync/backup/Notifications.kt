package io.github.xsaveopt.cryptsync.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        val progress = NotificationChannel(
            CHANNEL_ID,
            "Backup progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shows compression and upload progress" }
        val alerts = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Backup alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Warns when a backup is paused or needs attention" }
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(alerts)
    }

    fun alert(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(ALERT_ID, notification)
    }

    fun build(title: String, text: String, progress: Int, max: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (max > 0 || indeterminate) setProgress(max, progress, indeterminate)
            }
            .build()

    companion object {
        const val CHANNEL_ID = "cryptsync_backup"
        const val ALERT_CHANNEL_ID = "cryptsync_alerts"
        const val NOTIFICATION_ID = 1001
        const val ALERT_ID = 1002
    }
}
