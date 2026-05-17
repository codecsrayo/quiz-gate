package com.example.ask

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Prefs {
    private const val FILE = "quizgate_prefs"

    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_API_URL = "api_url"
    private const val KEY_GLOSSARY_API_URL = "glossary_api_url"
    private const val KEY_PENDING_UNLOCK_PKG = "pending_unlock_pkg"
    private const val KEY_PENDING_UNLOCK_UNTIL = "pending_unlock_until_ms"
    private const val KEY_LAST_BLOCKED_PKG = "last_blocked_package"
    private const val KEY_LAST_FETCH_MS = "last_fetch_ms"
    private const val KEY_LANG = "lang"
    private const val KEY_MAX_IN_APP_MIN = "max_in_app_min"
    private const val KEY_ENABLED_DOMAINS = "enabled_domains"
    private const val KEY_RECENT_QUESTION_IDS = "recent_question_ids"
    private const val KEY_QUESTION_STATS = "question_stats"
    private const val KEY_LAST_GLOSSARY_FETCH_MS = "last_glossary_fetch_ms"
    private const val KEY_GLOSSARY_PUSH_ENABLED = "glossary_push_enabled"
    private const val KEY_GLOSSARY_PUSH_INTERVAL_MIN = "glossary_push_interval_min"
    private const val KEY_RECENT_GLOSSARY_IDS = "recent_glossary_ids"

    const val DEFAULT_API_URL = "https://codecsrayo.com/api/quiz/practitioner"
    const val DEFAULT_GLOSSARY_API_URL = "https://codecsrayo.com/api/quiz/practitioner/synthetic/glossary"
    const val DEFAULT_GLOSSARY_PUSH_INTERVAL_MIN = 60
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

    fun getGlossaryApiUrl(ctx: Context): String =
        sp(ctx).getString(KEY_GLOSSARY_API_URL, DEFAULT_GLOSSARY_API_URL) ?: DEFAULT_GLOSSARY_API_URL

    fun setGlossaryApiUrl(ctx: Context, url: String) {
        sp(ctx).edit().putString(KEY_GLOSSARY_API_URL, url).apply()
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

    fun setLastGlossaryFetch(ctx: Context, ms: Long) {
        sp(ctx).edit().putLong(KEY_LAST_GLOSSARY_FETCH_MS, ms).apply()
    }

    fun getLastGlossaryFetch(ctx: Context): Long =
        sp(ctx).getLong(KEY_LAST_GLOSSARY_FETCH_MS, 0L)

    fun isGlossaryPushEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_GLOSSARY_PUSH_ENABLED, false)

    fun setGlossaryPushEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_GLOSSARY_PUSH_ENABLED, enabled).apply()
    }

    fun getGlossaryPushIntervalMin(ctx: Context): Int =
        sp(ctx).getInt(KEY_GLOSSARY_PUSH_INTERVAL_MIN, DEFAULT_GLOSSARY_PUSH_INTERVAL_MIN)

    fun setGlossaryPushIntervalMin(ctx: Context, minutes: Int) {
        sp(ctx).edit().putInt(KEY_GLOSSARY_PUSH_INTERVAL_MIN, minutes.coerceAtLeast(15)).apply()
    }

    fun getRecentGlossaryIds(ctx: Context): List<String> {
        val raw = sp(ctx).getString(KEY_RECENT_GLOSSARY_IDS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split('|')
    }

    fun pushRecentGlossaryId(ctx: Context, id: String, maxSize: Int) {
        if (maxSize <= 0) return
        val current = getRecentGlossaryIds(ctx).toMutableList()
        current.remove(id)
        current.add(id)
        while (current.size > maxSize) current.removeAt(0)
        sp(ctx).edit().putString(KEY_RECENT_GLOSSARY_IDS, current.joinToString("|")).apply()
    }

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

    data class QStat(val shown: Int, val correct: Int, val wrong: Int)

    fun getStats(ctx: Context): Map<String, QStat> {
        val raw = sp(ctx).getString(KEY_QUESTION_STATS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val out = HashMap<String, QStat>(obj.length())
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = obj.optJSONObject(k) ?: continue
                out[k] = QStat(v.optInt("s", 0), v.optInt("c", 0), v.optInt("w", 0))
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun saveStats(ctx: Context, stats: Map<String, QStat>) {
        val obj = JSONObject()
        for ((k, v) in stats) {
            obj.put(k, JSONObject().put("s", v.shown).put("c", v.correct).put("w", v.wrong))
        }
        sp(ctx).edit().putString(KEY_QUESTION_STATS, obj.toString()).apply()
    }

    fun recordShown(ctx: Context, id: String) {
        val stats = getStats(ctx).toMutableMap()
        val s = stats[id] ?: QStat(0, 0, 0)
        stats[id] = s.copy(shown = s.shown + 1)
        saveStats(ctx, stats)
    }

    fun recordAnswer(ctx: Context, id: String, correct: Boolean) {
        val stats = getStats(ctx).toMutableMap()
        val s = stats[id] ?: QStat(0, 0, 0)
        stats[id] = if (correct) s.copy(correct = s.correct + 1) else s.copy(wrong = s.wrong + 1)
        saveStats(ctx, stats)
    }
}
