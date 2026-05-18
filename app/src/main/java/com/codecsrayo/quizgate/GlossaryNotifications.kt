package com.codecsrayo.quizgate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object GlossaryNotifications {

    const val CHANNEL_ID = "quizgate_glossary"
    private const val BASE_NOTIFICATION_ID = 2000

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val res = LocaleManager.wrap(ctx)
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.glossary_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = res.getString(R.string.glossary_channel_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun postTerm(ctx: Context, term: GlossaryTerm, lang: String) {
        ensureChannel(ctx)
        val res = LocaleManager.wrap(ctx, lang)
        val title = if (term.name.isBlank()) term.symbol else "${term.symbol} · ${term.name}"
        val body = term.descFor(lang)
        val html = StringBuilder()
        if (term.category.isNotBlank()) {
            html.append("<b>")
            html.append(Html.escapeHtml(term.category))
            html.append("</b><br><br>")
        }
        html.append(Html.escapeHtml(body).replace("\n", "<br>"))
        if (term.aliases.isNotEmpty()) {
            html.append("<br><br><b>")
            html.append(Html.escapeHtml(res.getString(R.string.glossary_alias_label)))
            html.append("</b> ")
            html.append(Html.escapeHtml(term.aliases.joinToString(" · ")))
        }
        val expanded: CharSequence = Html.fromHtml(html.toString(), Html.FROM_HTML_MODE_LEGACY)
        val smallIcon = if (term.synthetic) R.drawable.ic_science
            else android.R.drawable.ic_menu_info_details
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        val id = BASE_NOTIFICATION_ID + (term.symbol.hashCode() and 0x7FFF)
        runCatching { NotificationManagerCompat.from(ctx).notify(id, builder.build()) }
    }
}
