package com.codecsrayo.quizgate

import android.content.SharedPreferences

/**
 * In-memory [PrefsBackend] for unit tests. Stores every value in plain Kotlin
 * collections and ignores the SharedPreferences listener API — tests usually
 * drive state changes directly so a no-op is sufficient.
 */
class FakePrefsBackend : PrefsBackend {

    private var blocked: Set<String> = BlockableApps.DEFAULT_PACKAGES
    private var apiUrl: String = Prefs.DEFAULT_API_URL
    private var glossaryApiUrl: String = Prefs.DEFAULT_GLOSSARY_API_URL
    private var lang: String = Prefs.DEFAULT_LANG
    private var pendingPkg: String? = null
    private var pendingUntil: Long = 0L
    private var lastBlockedPkg: String? = null
    private var lastFetch: Long = 0L
    private var lastGlossaryFetch: Long = 0L
    private var glossaryPushEnabled: Boolean = false
    private var glossaryPushInterval: Int = Prefs.DEFAULT_GLOSSARY_PUSH_INTERVAL_MIN
    private var glossaryActiveStart: Int = Prefs.DEFAULT_GLOSSARY_ACTIVE_START_MIN
    private var glossaryActiveEnd: Int = Prefs.DEFAULT_GLOSSARY_ACTIVE_END_MIN
    private var recentGlossary: List<String> = emptyList()
    private val lastSeen: MutableMap<String, Long> = HashMap()
    private val sessionStart: MutableMap<String, Long> = HashMap()
    private var maxInAppMin: Int = Prefs.DEFAULT_MAX_IN_APP_MIN
    private var enabledDomains: Set<String>? = null
    private var includeOfficial: Boolean = true
    private var includeSynthetic: Boolean = true
    private var recentQuestion: List<String> = emptyList()
    private var stats: Map<String, Prefs.QStat> = emptyMap()

    var nowProvider: () -> Long = System::currentTimeMillis

    override fun getBlockedPackages() = blocked
    override fun setBlockedPackages(pkgs: Set<String>) { blocked = pkgs }

    override fun getApiUrl() = apiUrl
    override fun setApiUrl(url: String) { apiUrl = url }

    override fun getGlossaryApiUrl() = glossaryApiUrl
    override fun setGlossaryApiUrl(url: String) { glossaryApiUrl = url }

    override fun getLang() = lang
    override fun setLang(lang: String) { this.lang = lang }

    override fun setPendingUnlock(pkg: String) {
        pendingPkg = pkg
        pendingUntil = nowProvider() + 60_000L
    }
    override fun consumePendingUnlock(pkg: String): Boolean {
        val stored = pendingPkg ?: return false
        val now = nowProvider()
        if (stored != pkg || now > pendingUntil) {
            if (now > pendingUntil) clearPendingUnlock()
            return false
        }
        clearPendingUnlock()
        return true
    }
    override fun clearPendingUnlock() { pendingPkg = null; pendingUntil = 0L }

    override fun setLastBlockedPackage(pkg: String?) { lastBlockedPkg = pkg }
    override fun getLastBlockedPackage(): String? = lastBlockedPkg

    override fun setLastFetch(ms: Long) { lastFetch = ms }
    override fun getLastFetch(): Long = lastFetch

    override fun setLastGlossaryFetch(ms: Long) { lastGlossaryFetch = ms }
    override fun getLastGlossaryFetch(): Long = lastGlossaryFetch

    override fun isGlossaryPushEnabled() = glossaryPushEnabled
    override fun setGlossaryPushEnabled(enabled: Boolean) { glossaryPushEnabled = enabled }

    override fun getGlossaryPushIntervalMin() = glossaryPushInterval
    override fun setGlossaryPushIntervalMin(minutes: Int) {
        glossaryPushInterval = minutes.coerceAtLeast(15)
    }

    override fun getGlossaryActiveStartMin() = glossaryActiveStart
    override fun setGlossaryActiveStartMin(min: Int) { glossaryActiveStart = min.coerceIn(0, 1440) }
    override fun getGlossaryActiveEndMin() = glossaryActiveEnd
    override fun setGlossaryActiveEndMin(min: Int) { glossaryActiveEnd = min.coerceIn(0, 1440) }

    override fun getRecentGlossaryIds() = recentGlossary
    override fun pushRecentGlossaryId(id: String, maxSize: Int) {
        recentGlossary = RecentQueue.push(recentGlossary, id, maxSize)
    }

    override fun touchLastSeen(pkg: String) { lastSeen[pkg] = nowProvider() }
    override fun isWithinSessionWindow(pkg: String): Boolean {
        val ls = lastSeen[pkg] ?: return false
        return nowProvider() - ls < 5 * 60_000L
    }
    override fun clearLastSeen(pkg: String) { lastSeen.remove(pkg) }

    override fun getMaxInAppMinutes() = maxInAppMin
    override fun setMaxInAppMinutes(minutes: Int) { maxInAppMin = minutes.coerceAtLeast(0) }
    override fun getMaxInAppMs(): Long = maxInAppMin.toLong() * 60_000L

    override fun getSessionStart(pkg: String): Long = sessionStart[pkg] ?: 0L
    override fun setSessionStart(pkg: String, ms: Long) { sessionStart[pkg] = ms }
    override fun clearSessionStart(pkg: String) { sessionStart.remove(pkg) }

    override fun getEnabledDomainsOrNull(): Set<String>? = enabledDomains?.ifEmpty { null }
    override fun setEnabledDomains(domains: Set<String>) { enabledDomains = domains }

    override fun isIncludeOfficial() = includeOfficial
    override fun setIncludeOfficial(included: Boolean) { includeOfficial = included }

    override fun isIncludeSynthetic() = includeSynthetic
    override fun setIncludeSynthetic(included: Boolean) { includeSynthetic = included }

    override fun getRecentQuestionIds() = recentQuestion
    override fun pushRecentQuestionId(id: String, maxSize: Int) {
        recentQuestion = RecentQueue.push(recentQuestion, id, maxSize)
    }

    override fun getStats() = stats
    override fun recordShown(id: String) {
        val cur = stats.toMutableMap()
        val s = cur[id] ?: Prefs.QStat(0, 0, 0)
        cur[id] = s.copy(shown = s.shown + 1)
        stats = cur
    }
    override fun recordAnswer(id: String, correct: Boolean) {
        val cur = stats.toMutableMap()
        val s = cur[id] ?: Prefs.QStat(0, 0, 0)
        cur[id] = if (correct) s.copy(correct = s.correct + 1) else s.copy(wrong = s.wrong + 1)
        stats = cur
    }

    override fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        // no-op
    }
    override fun unregisterChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        // no-op
    }
}
