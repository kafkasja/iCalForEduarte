package dev.eduartesync.sync

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
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.ui.MainActivity

object Notifier {
    private const val CHANNEL = "sync"
    private const val ID_STATUS = 1

    fun loginRequired(context: Context) = show(
        context,
        title = "Log in to Eduarte again",
        text = "Tap to log in so your schedule keeps syncing.",
        intent = Intent(context, LoginActivity::class.java),
    )

    fun syncFailed(context: Context, reason: String?) = show(
        context,
        title = "Schedule sync failed",
        text = reason ?: "Tap to try again.",
        intent = Intent(context, MainActivity::class.java),
    )

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID_STATUS)

    private fun show(context: Context, title: String, text: String, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sync status", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pending = PendingIntent.getActivity(
            context, 0, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_STATUS, notification)
    }
}
