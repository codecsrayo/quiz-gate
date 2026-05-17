package com.example.ask

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object GlossaryNotifications {

    const val CHANNEL_ID = "quizgate_glossary"
    private const val CHANNEL_NAME = "Glosario AWS"
    private const val CHANNEL_DESC = "Términos del glosario AWS programados"
    private const val BASE_NOTIFICATION_ID = 2000

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun postTerm(ctx: Context, term: GlossaryTerm, lang: String) {
        ensureChannel(ctx)
        val baseTitle = if (term.name.isBlank()) term.symbol else "${term.symbol} · ${term.name}"
        val title = if (term.synthetic) "🧪 $baseTitle" else baseTitle
        val body = term.descFor(lang)
        val html = StringBuilder()
        if (term.synthetic) {
            html.append("<font color='#E65100'><b>🧪 DATOS SINTÉTICOS</b></font>")
            html.append("<br><i><font color='#757575'>Generado automáticamente. Contrasta antes de aplicar.</font></i>")
            html.append("<br><br>")
        }
        if (term.category.isNotBlank()) {
            html.append("<b>")
            html.append(Html.escapeHtml(term.category))
            html.append("</b><br><br>")
        }
        html.append(Html.escapeHtml(body).replace("\n", "<br>"))
        if (term.aliases.isNotEmpty()) {
            html.append("<br><br><b>Alias:</b> ")
            html.append(Html.escapeHtml(term.aliases.joinToString(" · ")))
        }
        val expanded: CharSequence = Html.fromHtml(html.toString(), Html.FROM_HTML_MODE_LEGACY)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (term.synthetic) {
            builder.setSubText("🧪 Generado sintéticamente")
            builder.setColor(0xFFE65100.toInt())
        }
        val id = BASE_NOTIFICATION_ID + (term.symbol.hashCode() and 0x7FFF)
        runCatching { NotificationManagerCompat.from(ctx).notify(id, builder.build()) }
    }
}
