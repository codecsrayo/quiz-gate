package com.example.ask

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Drives the app UI language from the in-app [Prefs.getLang] preference ("es" / "en")
 * instead of the system locale, so the same ES/EN toggle that switches quiz content
 * also switches every static string.
 */
object LocaleManager {

    /** Build a context whose resources resolve to [lang]. */
    fun wrap(base: Context, lang: String = Prefs.getLang(base)): Context {
        val locale = Locale.forLanguageTag(lang)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

/**
 * Re-scopes [content] so `stringResource` (and any `LocalContext` lookup) resolves
 * against [lang]. Changing [lang] recomposes in place — no Activity recreation, so
 * the current quiz question and screen state are preserved.
 */
@Composable
fun LocalizedContent(lang: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localizedContext = remember(lang, context) { LocaleManager.wrap(context, lang) }
    val configuration = remember(lang, context) {
        Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(lang))
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}
