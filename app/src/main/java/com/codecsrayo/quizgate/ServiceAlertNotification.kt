package com.codecsrayo.quizgate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * High-priority alert raised by [ServiceGuardWorker] when the accessibility
 * service — and therefore app blocking — is no longer running. Only the user
 * can rebind an accessibility service, so the recovery path is: notify, and on
 * tap open Accessibility settings where they can toggle it back on.
 */
object ServiceAlertNotification {

    const val CHANNEL_ID = "quizgate_alert"
    private const val NOTIFICATION_ID = 1002

    fun post(ctx: Context) {
        ensureChannel(ctx)
        val res = LocaleManager.wrap(ctx)
        val component = "${ctx.packageName}/${BlockerAccessibilityService::class.java.name}"
        val settings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            // OEM skins that honour this scroll to and highlight our row.
            .putExtra(":settings:fragment_args_key", component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 0, settings,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = res.getString(R.string.service_alert_text)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(res.getString(R.string.service_alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID) }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val res = LocaleManager.wrap(ctx)
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.service_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = res.getString(R.string.service_alert_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }
}
