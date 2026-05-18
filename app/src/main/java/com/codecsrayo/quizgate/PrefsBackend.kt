package com.codecsrayo.quizgate

import android.content.Context
import android.content.SharedPreferences

/**
 * The complete preferences surface the app consumes. Defined as an interface so
 * tests can inject an in-memory fake via [Prefs.installForTest]; production
 * code stays on the [DefaultPrefsBackend] implementation that wraps real
 * SharedPreferences.
 *
 * Method names mirror the previous `Prefs.foo(ctx)` shape but drop the Context
 * parameter — backends own whatever scope they need.
 */
interface PrefsBackend {
    fun getBlockedPackages(): Set<String>
    fun setBlockedPackages(pkgs: Set<String>)

    fun getApiUrl(): String
    fun setApiUrl(url: String)

    fun getGlossaryApiUrl(): String
    fun setGlossaryApiUrl(url: String)

    fun getLang(): String
    fun setLang(lang: String)

    fun setPendingUnlock(pkg: String)
    fun consumePendingUnlock(pkg: String): Boolean
    fun clearPendingUnlock()

    fun setLastBlockedPackage(pkg: String?)
    fun getLastBlockedPackage(): String?

    fun setLastFetch(ms: Long)
    fun getLastFetch(): Long

    fun setLastGlossaryFetch(ms: Long)
    fun getLastGlossaryFetch(): Long

    fun isGlossaryPushEnabled(): Boolean
    fun setGlossaryPushEnabled(enabled: Boolean)

    fun getGlossaryPushIntervalMin(): Int
    fun setGlossaryPushIntervalMin(minutes: Int)

    /** Minute-of-day [0, 1440] when the glossary-push active window opens. */
    fun getGlossaryActiveStartMin(): Int
    fun setGlossaryActiveStartMin(min: Int)

    /** Minute-of-day [0, 1440] when the glossary-push active window closes (exclusive). */
    fun getGlossaryActiveEndMin(): Int
    fun setGlossaryActiveEndMin(min: Int)

    fun getRecentGlossaryIds(): List<String>
    fun pushRecentGlossaryId(id: String, maxSize: Int)

    fun touchLastSeen(pkg: String)
    fun isWithinSessionWindow(pkg: String): Boolean
    fun clearLastSeen(pkg: String)

    fun getMaxInAppMinutes(): Int
    fun setMaxInAppMinutes(minutes: Int)
    fun getMaxInAppMs(): Long

    fun getSessionStart(pkg: String): Long
    fun setSessionStart(pkg: String, ms: Long)
    fun clearSessionStart(pkg: String)

    fun getEnabledDomainsOrNull(): Set<String>?
    fun setEnabledDomains(domains: Set<String>)

    fun isIncludeOfficial(): Boolean
    fun setIncludeOfficial(included: Boolean)

    fun isIncludeSynthetic(): Boolean
    fun setIncludeSynthetic(included: Boolean)

    fun getRecentQuestionIds(): List<String>
    fun pushRecentQuestionId(id: String, maxSize: Int)

    fun getStats(): Map<String, Prefs.QStat>
    fun recordShown(id: String)
    fun recordAnswer(id: String, correct: Boolean)

    /**
     * Implementations bridge to the real SharedPreferences change stream when
     * available. In-memory fakes typically no-op these — tests usually drive
     * state changes directly.
     */
    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
}

internal class DefaultPrefsBackend(context: Context) : PrefsBackend {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile private var statsCache: Map<String, Prefs.QStat>? = null
    private val statsLock = Any()

    override fun getBlockedPackages(): Set<String> =
        sp.getStringSet(Prefs.KEY_BLOCKED, Prefs.DEFAULT_BLOCKED) ?: Prefs.DEFAULT_BLOCKED

    override fun setBlockedPackages(pkgs: Set<String>) {
        sp.edit().putStringSet(Prefs.KEY_BLOCKED, pkgs).apply()
    }

    override fun getApiUrl(): String =
        sp.getString(KEY_API_URL, Prefs.DEFAULT_API_URL) ?: Prefs.DEFAULT_API_URL

    override fun setApiUrl(url: String) {
        sp.edit().putString(KEY_API_URL, url).apply()
    }

    override fun getGlossaryApiUrl(): String =
        sp.getString(KEY_GLOSSARY_API_URL, Prefs.DEFAULT_GLOSSARY_API_URL)
            ?: Prefs.DEFAULT_GLOSSARY_API_URL

    override fun setGlossaryApiUrl(url: String) {
        sp.edit().putString(KEY_GLOSSARY_API_URL, url).apply()
    }

    override fun getLang(): String = sp.getString(KEY_LANG, Prefs.DEFAULT_LANG) ?: Prefs.DEFAULT_LANG

    override fun setLang(lang: String) {
        sp.edit().putString(KEY_LANG, lang).apply()
    }

    override fun setPendingUnlock(pkg: String) {
        sp.edit()
            .putString(KEY_PENDING_UNLOCK_PKG, pkg)
            .putLong(KEY_PENDING_UNLOCK_UNTIL, System.currentTimeMillis() + PENDING_UNLOCK_TTL_MS)
            .apply()
    }

    override fun consumePendingUnlock(pkg: String): Boolean {
        val storedPkg = sp.getString(KEY_PENDING_UNLOCK_PKG, null) ?: return false
        val until = sp.getLong(KEY_PENDING_UNLOCK_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (storedPkg != pkg || now > until) {
            if (now > until) clearPendingUnlock()
            return false
        }
        clearPendingUnlock()
        return true
    }

    override fun clearPendingUnlock() {
        sp.edit().remove(KEY_PENDING_UNLOCK_PKG).remove(KEY_PENDING_UNLOCK_UNTIL).apply()
    }

    override fun setLastBlockedPackage(pkg: String?) {
        sp.edit().putString(KEY_LAST_BLOCKED_PKG, pkg).apply()
    }

    override fun getLastBlockedPackage(): String? = sp.getString(KEY_LAST_BLOCKED_PKG, null)

    override fun setLastFetch(ms: Long) { sp.edit().putLong(KEY_LAST_FETCH_MS, ms).apply() }
    override fun getLastFetch(): Long = sp.getLong(KEY_LAST_FETCH_MS, 0L)

    override fun setLastGlossaryFetch(ms: Long) {
        sp.edit().putLong(KEY_LAST_GLOSSARY_FETCH_MS, ms).apply()
    }
    override fun getLastGlossaryFetch(): Long = sp.getLong(KEY_LAST_GLOSSARY_FETCH_MS, 0L)

    override fun isGlossaryPushEnabled(): Boolean = sp.getBoolean(KEY_GLOSSARY_PUSH_ENABLED, false)
    override fun setGlossaryPushEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_GLOSSARY_PUSH_ENABLED, enabled).apply()
    }

    override fun getGlossaryPushIntervalMin(): Int =
        sp.getInt(KEY_GLOSSARY_PUSH_INTERVAL_MIN, Prefs.DEFAULT_GLOSSARY_PUSH_INTERVAL_MIN)
    override fun setGlossaryPushIntervalMin(minutes: Int) {
        sp.edit().putInt(KEY_GLOSSARY_PUSH_INTERVAL_MIN, minutes.coerceAtLeast(15)).apply()
    }

    override fun getGlossaryActiveStartMin(): Int =
        sp.getInt(KEY_GLOSSARY_ACTIVE_START_MIN, Prefs.DEFAULT_GLOSSARY_ACTIVE_START_MIN)
    override fun setGlossaryActiveStartMin(min: Int) {
        sp.edit().putInt(KEY_GLOSSARY_ACTIVE_START_MIN, min.coerceIn(0, 1440)).apply()
    }

    override fun getGlossaryActiveEndMin(): Int =
        sp.getInt(KEY_GLOSSARY_ACTIVE_END_MIN, Prefs.DEFAULT_GLOSSARY_ACTIVE_END_MIN)
    override fun setGlossaryActiveEndMin(min: Int) {
        sp.edit().putInt(KEY_GLOSSARY_ACTIVE_END_MIN, min.coerceIn(0, 1440)).apply()
    }

    override fun getRecentGlossaryIds(): List<String> =
        RecentQueue.decode(sp.getString(KEY_RECENT_GLOSSARY_IDS, null))
    override fun pushRecentGlossaryId(id: String, maxSize: Int) {
        val next = RecentQueue.push(getRecentGlossaryIds(), id, maxSize)
        sp.edit().putString(KEY_RECENT_GLOSSARY_IDS, RecentQueue.encode(next)).apply()
    }

    override fun touchLastSeen(pkg: String) {
        sp.edit().putLong("$KEY_LAST_SEEN_PREFIX$pkg", System.currentTimeMillis()).apply()
    }
    override fun isWithinSessionWindow(pkg: String): Boolean {
        val ls = sp.getLong("$KEY_LAST_SEEN_PREFIX$pkg", 0L)
        if (ls == 0L) return false
        return System.currentTimeMillis() - ls < SESSION_WINDOW_MS
    }
    override fun clearLastSeen(pkg: String) {
        sp.edit().remove("$KEY_LAST_SEEN_PREFIX$pkg").apply()
    }

    override fun getMaxInAppMinutes(): Int = sp.getInt(KEY_MAX_IN_APP_MIN, Prefs.DEFAULT_MAX_IN_APP_MIN)
    override fun setMaxInAppMinutes(minutes: Int) {
        sp.edit().putInt(KEY_MAX_IN_APP_MIN, minutes.coerceAtLeast(0)).apply()
    }
    override fun getMaxInAppMs(): Long = getMaxInAppMinutes().toLong() * 60_000L

    override fun getSessionStart(pkg: String): Long = sp.getLong("$KEY_SESSION_START_PREFIX$pkg", 0L)
    override fun setSessionStart(pkg: String, ms: Long) {
        sp.edit().putLong("$KEY_SESSION_START_PREFIX$pkg", ms).apply()
    }
    override fun clearSessionStart(pkg: String) {
        sp.edit().remove("$KEY_SESSION_START_PREFIX$pkg").apply()
    }

    override fun getEnabledDomainsOrNull(): Set<String>? {
        val set = sp.getStringSet(KEY_ENABLED_DOMAINS, null) ?: return null
        return set.ifEmpty { null }
    }
    override fun setEnabledDomains(domains: Set<String>) {
        sp.edit().putStringSet(KEY_ENABLED_DOMAINS, domains).apply()
    }

    override fun isIncludeOfficial(): Boolean = sp.getBoolean(KEY_INCLUDE_OFFICIAL, true)
    override fun setIncludeOfficial(included: Boolean) {
        sp.edit().putBoolean(KEY_INCLUDE_OFFICIAL, included).apply()
    }
    override fun isIncludeSynthetic(): Boolean = sp.getBoolean(KEY_INCLUDE_SYNTHETIC, true)
    override fun setIncludeSynthetic(included: Boolean) {
        sp.edit().putBoolean(KEY_INCLUDE_SYNTHETIC, included).apply()
    }

    override fun getRecentQuestionIds(): List<String> =
        RecentQueue.decode(sp.getString(KEY_RECENT_QUESTION_IDS, null))
    override fun pushRecentQuestionId(id: String, maxSize: Int) {
        val next = RecentQueue.push(getRecentQuestionIds(), id, maxSize)
        sp.edit().putString(KEY_RECENT_QUESTION_IDS, RecentQueue.encode(next)).apply()
    }

    override fun getStats(): Map<String, Prefs.QStat> {
        statsCache?.let { return it }
        return synchronized(statsLock) {
            statsCache ?: StatsCodec.decode(sp.getString(KEY_QUESTION_STATS, null))
                .also { statsCache = it }
        }
    }

    override fun recordShown(id: String) {
        synchronized(statsLock) {
            val stats = getStats().toMutableMap()
            val s = stats[id] ?: Prefs.QStat(0, 0, 0)
            stats[id] = s.copy(shown = s.shown + 1)
            saveStats(stats)
        }
    }

    override fun recordAnswer(id: String, correct: Boolean) {
        synchronized(statsLock) {
            val stats = getStats().toMutableMap()
            val s = stats[id] ?: Prefs.QStat(0, 0, 0)
            stats[id] = if (correct) s.copy(correct = s.correct + 1) else s.copy(wrong = s.wrong + 1)
            saveStats(stats)
        }
    }

    private fun saveStats(stats: Map<String, Prefs.QStat>) {
        statsCache = stats
        sp.edit().putString(KEY_QUESTION_STATS, StatsCodec.encode(stats)).apply()
    }

    override fun registerChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val FILE = "quizgate_prefs"
        const val KEY_API_URL = "api_url"
        const val KEY_GLOSSARY_API_URL = "glossary_api_url"
        const val KEY_PENDING_UNLOCK_PKG = "pending_unlock_pkg"
        const val KEY_PENDING_UNLOCK_UNTIL = "pending_unlock_until_ms"
        const val KEY_LAST_BLOCKED_PKG = "last_blocked_package"
        const val KEY_LAST_FETCH_MS = "last_fetch_ms"
        const val KEY_LANG = "lang"
        const val KEY_MAX_IN_APP_MIN = "max_in_app_min"
        const val KEY_ENABLED_DOMAINS = "enabled_domains"
        const val KEY_INCLUDE_OFFICIAL = "include_official"
        const val KEY_INCLUDE_SYNTHETIC = "include_synthetic"
        const val KEY_RECENT_QUESTION_IDS = "recent_question_ids"
        const val KEY_QUESTION_STATS = "question_stats"
        const val KEY_LAST_GLOSSARY_FETCH_MS = "last_glossary_fetch_ms"
        const val KEY_GLOSSARY_PUSH_ENABLED = "glossary_push_enabled"
        const val KEY_GLOSSARY_PUSH_INTERVAL_MIN = "glossary_push_interval_min"
        const val KEY_GLOSSARY_ACTIVE_START_MIN = "glossary_active_start_min"
        const val KEY_GLOSSARY_ACTIVE_END_MIN = "glossary_active_end_min"
        const val KEY_RECENT_GLOSSARY_IDS = "recent_glossary_ids"
        const val PENDING_UNLOCK_TTL_MS = 60_000L
        const val SESSION_WINDOW_MS = 5 * 60_000L
        const val KEY_LAST_SEEN_PREFIX = "last_seen_"
        const val KEY_SESSION_START_PREFIX = "session_start_"
    }
}
