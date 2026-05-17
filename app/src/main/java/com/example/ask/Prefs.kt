package com.example.ask

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "quizgate_prefs"

    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_API_URL = "api_url"
    private const val KEY_PENDING_UNLOCK_PKG = "pending_unlock_pkg"
    private const val KEY_PENDING_UNLOCK_UNTIL = "pending_unlock_until_ms"
    private const val KEY_LAST_BLOCKED_PKG = "last_blocked_package"
    private const val KEY_LAST_FETCH_MS = "last_fetch_ms"
    private const val KEY_LANG = "lang"
    private const val KEY_MAX_IN_APP_MIN = "max_in_app_min"
    private const val KEY_ENABLED_DOMAINS = "enabled_domains"
    private const val KEY_RECENT_QUESTION_IDS = "recent_question_ids"

    const val DEFAULT_API_URL = "https://codecsrayo.com/api/quiz/practitioner"
    const val DEFAULT_LANG = "es"
    const val DEFAULT_MAX_IN_APP_MIN = 10
    private const val PENDING_UNLOCK_TTL_MS = 60_000L
    private const val SESSION_WINDOW_MS = 5 * 60_000L
    private const val KEY_LAST_SEEN_PREFIX = "last_seen_"
    private const val KEY_SESSION_START_PREFIX = "session_start_"

    val DEFAULT_BLOCKED: Set<String> = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.whatsapp",
        "com.instagram.android",
    )

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getBlockedPackages(ctx: Context): Set<String> =
        sp(ctx).getStringSet(KEY_BLOCKED, DEFAULT_BLOCKED) ?: DEFAULT_BLOCKED

    fun setBlockedPackages(ctx: Context, pkgs: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_BLOCKED, pkgs).apply()
    }

    fun getApiUrl(ctx: Context): String =
        sp(ctx).getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL

    fun setApiUrl(ctx: Context, url: String) {
        sp(ctx).edit().putString(KEY_API_URL, url).apply()
    }

    fun getLang(ctx: Context): String =
        sp(ctx).getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG

    fun setLang(ctx: Context, lang: String) {
        sp(ctx).edit().putString(KEY_LANG, lang).apply()
    }

    fun setPendingUnlock(ctx: Context, pkg: String) {
        sp(ctx).edit()
            .putString(KEY_PENDING_UNLOCK_PKG, pkg)
            .putLong(KEY_PENDING_UNLOCK_UNTIL, System.currentTimeMillis() + PENDING_UNLOCK_TTL_MS)
            .apply()
    }

    fun consumePendingUnlock(ctx: Context, pkg: String): Boolean {
        val s = sp(ctx)
        val storedPkg = s.getString(KEY_PENDING_UNLOCK_PKG, null) ?: return false
        val until = s.getLong(KEY_PENDING_UNLOCK_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (storedPkg != pkg || now > until) {
            if (now > until) clearPendingUnlock(ctx)
            return false
        }
        clearPendingUnlock(ctx)
        return true
    }

    fun clearPendingUnlock(ctx: Context) {
        sp(ctx).edit()
            .remove(KEY_PENDING_UNLOCK_PKG)
            .remove(KEY_PENDING_UNLOCK_UNTIL)
            .apply()
    }

    fun setLastBlockedPackage(ctx: Context, pkg: String?) {
        sp(ctx).edit().putString(KEY_LAST_BLOCKED_PKG, pkg).apply()
    }

    fun getLastBlockedPackage(ctx: Context): String? =
        sp(ctx).getString(KEY_LAST_BLOCKED_PKG, null)

    fun setLastFetch(ctx: Context, ms: Long) {
        sp(ctx).edit().putLong(KEY_LAST_FETCH_MS, ms).apply()
    }

    fun getLastFetch(ctx: Context): Long =
        sp(ctx).getLong(KEY_LAST_FETCH_MS, 0L)

    fun touchLastSeen(ctx: Context, pkg: String) {
        sp(ctx).edit().putLong("$KEY_LAST_SEEN_PREFIX$pkg", System.currentTimeMillis()).apply()
    }

    fun isWithinSessionWindow(ctx: Context, pkg: String): Boolean {
        val ls = sp(ctx).getLong("$KEY_LAST_SEEN_PREFIX$pkg", 0L)
        if (ls == 0L) return false
        return System.currentTimeMillis() - ls < SESSION_WINDOW_MS
    }

    fun clearLastSeen(ctx: Context, pkg: String) {
        sp(ctx).edit().remove("$KEY_LAST_SEEN_PREFIX$pkg").apply()
    }

    fun getMaxInAppMinutes(ctx: Context): Int =
        sp(ctx).getInt(KEY_MAX_IN_APP_MIN, DEFAULT_MAX_IN_APP_MIN)

    fun setMaxInAppMinutes(ctx: Context, minutes: Int) {
        sp(ctx).edit().putInt(KEY_MAX_IN_APP_MIN, minutes.coerceAtLeast(0)).apply()
    }

    fun getMaxInAppMs(ctx: Context): Long =
        getMaxInAppMinutes(ctx).toLong() * 60_000L

    fun getSessionStart(ctx: Context, pkg: String): Long =
        sp(ctx).getLong("$KEY_SESSION_START_PREFIX$pkg", 0L)

    fun setSessionStart(ctx: Context, pkg: String, ms: Long) {
        sp(ctx).edit().putLong("$KEY_SESSION_START_PREFIX$pkg", ms).apply()
    }

    fun clearSessionStart(ctx: Context, pkg: String) {
        sp(ctx).edit().remove("$KEY_SESSION_START_PREFIX$pkg").apply()
    }

    /** Empty set means "all domains enabled". */
    fun getEnabledDomains(ctx: Context): Set<String> =
        sp(ctx).getStringSet(KEY_ENABLED_DOMAINS, emptySet()) ?: emptySet()

    fun setEnabledDomains(ctx: Context, domains: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_ENABLED_DOMAINS, domains).apply()
    }

    fun getRecentQuestionIds(ctx: Context): List<String> {
        val raw = sp(ctx).getString(KEY_RECENT_QUESTION_IDS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split('|')
    }

    fun pushRecentQuestionId(ctx: Context, id: String, maxSize: Int) {
        if (maxSize <= 0) return
        val current = getRecentQuestionIds(ctx).toMutableList()
        current.remove(id)
        current.add(id)
        while (current.size > maxSize) current.removeAt(0)
        sp(ctx).edit().putString(KEY_RECENT_QUESTION_IDS, current.joinToString("|")).apply()
    }
}
