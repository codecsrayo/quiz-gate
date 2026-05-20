package com.codecsrayo.quizgate

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting

/**
 * Static facade preserving the historic `Prefs.foo(ctx)` callsite shape.
 * All operations are forwarded to a [PrefsBackend] that's lazily initialised
 * on first call. Tests swap it via [installForTest] / [reset].
 */
object Prefs {

    const val KEY_BLOCKED = "blocked_packages"

    const val DEFAULT_API_URL = "https://codecsrayo.com/api/quiz/practitioner/questions"
    const val DEFAULT_GLOSSARY_API_URL = "https://codecsrayo.com/api/quiz/practitioner/glossary"
    const val DEFAULT_GLOSSARY_PUSH_INTERVAL_MIN = 60
    const val DEFAULT_GLOSSARY_ACTIVE_START_MIN = 8 * 60   // 08:00
    const val DEFAULT_GLOSSARY_ACTIVE_END_MIN = 22 * 60    // 22:00
    const val DEFAULT_LANG = "es"
    const val DEFAULT_MAX_IN_APP_MIN = 10

    val DEFAULT_BLOCKED: Set<String> = BlockableApps.DEFAULT_PACKAGES

    data class QStat(val shown: Int, val correct: Int, val wrong: Int)

    @Volatile private var backend: PrefsBackend? = null
    private val initLock = Any()

    /**
     * Returns the active backend, lazily building a [DefaultPrefsBackend] from
     * [ctx] when none was installed. [ctx] is only dereferenced on first call
     * before a fake has been installed; once a fake or default exists it's
     * ignored, which is what lets unit tests pass null after [installForTest].
     */
    private fun b(ctx: Context?): PrefsBackend {
        backend?.let { return it }
        val nonNull = checkNotNull(ctx) {
            "Prefs accessed without a Context and no test backend installed"
        }
        return synchronized(initLock) {
            backend ?: DefaultPrefsBackend(nonNull.applicationContext).also { backend = it }
        }
    }

    @VisibleForTesting
    fun installForTest(fake: PrefsBackend) {
        backend = fake
    }

    @VisibleForTesting
    fun reset() {
        backend = null
    }

    fun registerChangeListener(
        ctx: Context?,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = b(ctx).registerChangeListener(listener)

    fun unregisterChangeListener(
        ctx: Context?,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = b(ctx).unregisterChangeListener(listener)

    fun getBlockedPackages(ctx: Context?): Set<String> = b(ctx).getBlockedPackages()
    fun setBlockedPackages(ctx: Context?, pkgs: Set<String>) = b(ctx).setBlockedPackages(pkgs)

    fun getApiUrl(ctx: Context?): String = b(ctx).getApiUrl()
    fun setApiUrl(ctx: Context?, url: String) = b(ctx).setApiUrl(url)

    fun getGlossaryApiUrl(ctx: Context?): String = b(ctx).getGlossaryApiUrl()
    fun setGlossaryApiUrl(ctx: Context?, url: String) = b(ctx).setGlossaryApiUrl(url)

    fun getLang(ctx: Context?): String = b(ctx).getLang()
    fun setLang(ctx: Context?, lang: String) = b(ctx).setLang(lang)

    fun setPendingUnlock(ctx: Context?, pkg: String) = b(ctx).setPendingUnlock(pkg)
    fun consumePendingUnlock(ctx: Context?, pkg: String): Boolean = b(ctx).consumePendingUnlock(pkg)
    fun clearPendingUnlock(ctx: Context?) = b(ctx).clearPendingUnlock()

    fun setLastBlockedPackage(ctx: Context?, pkg: String?) = b(ctx).setLastBlockedPackage(pkg)
    fun getLastBlockedPackage(ctx: Context?): String? = b(ctx).getLastBlockedPackage()

    fun setLastFetch(ctx: Context?, ms: Long) = b(ctx).setLastFetch(ms)
    fun getLastFetch(ctx: Context?): Long = b(ctx).getLastFetch()

    fun setLastGlossaryFetch(ctx: Context?, ms: Long) = b(ctx).setLastGlossaryFetch(ms)
    fun getLastGlossaryFetch(ctx: Context?): Long = b(ctx).getLastGlossaryFetch()

    fun isGlossaryPushEnabled(ctx: Context?): Boolean = b(ctx).isGlossaryPushEnabled()
    fun setGlossaryPushEnabled(ctx: Context?, enabled: Boolean) = b(ctx).setGlossaryPushEnabled(enabled)

    fun getGlossaryPushIntervalMin(ctx: Context?): Int = b(ctx).getGlossaryPushIntervalMin()
    fun setGlossaryPushIntervalMin(ctx: Context?, minutes: Int) =
        b(ctx).setGlossaryPushIntervalMin(minutes)

    fun getGlossaryActiveStartMin(ctx: Context?): Int = b(ctx).getGlossaryActiveStartMin()
    fun setGlossaryActiveStartMin(ctx: Context?, min: Int) = b(ctx).setGlossaryActiveStartMin(min)

    fun getGlossaryActiveEndMin(ctx: Context?): Int = b(ctx).getGlossaryActiveEndMin()
    fun setGlossaryActiveEndMin(ctx: Context?, min: Int) = b(ctx).setGlossaryActiveEndMin(min)

    fun getRecentGlossaryIds(ctx: Context?): List<String> = b(ctx).getRecentGlossaryIds()
    fun pushRecentGlossaryId(ctx: Context?, id: String, maxSize: Int) =
        b(ctx).pushRecentGlossaryId(id, maxSize)

    fun touchLastSeen(ctx: Context?, pkg: String) = b(ctx).touchLastSeen(pkg)
    fun isWithinSessionWindow(ctx: Context?, pkg: String): Boolean = b(ctx).isWithinSessionWindow(pkg)
    fun clearLastSeen(ctx: Context?, pkg: String) = b(ctx).clearLastSeen(pkg)

    fun getMaxInAppMinutes(ctx: Context?): Int = b(ctx).getMaxInAppMinutes()
    fun setMaxInAppMinutes(ctx: Context?, minutes: Int) = b(ctx).setMaxInAppMinutes(minutes)
    fun getMaxInAppMs(ctx: Context?): Long = b(ctx).getMaxInAppMs()

    fun getSessionStart(ctx: Context?, pkg: String): Long = b(ctx).getSessionStart(pkg)
    fun setSessionStart(ctx: Context?, pkg: String, ms: Long) = b(ctx).setSessionStart(pkg, ms)
    fun clearSessionStart(ctx: Context?, pkg: String) = b(ctx).clearSessionStart(pkg)

    fun getEnabledDomainsOrNull(ctx: Context?): Set<String>? = b(ctx).getEnabledDomainsOrNull()
    fun setEnabledDomains(ctx: Context?, domains: Set<String>) = b(ctx).setEnabledDomains(domains)

    fun isIncludeOfficial(ctx: Context?): Boolean = b(ctx).isIncludeOfficial()
    fun setIncludeOfficial(ctx: Context?, included: Boolean) = b(ctx).setIncludeOfficial(included)

    fun isIncludeSynthetic(ctx: Context?): Boolean = b(ctx).isIncludeSynthetic()
    fun setIncludeSynthetic(ctx: Context?, included: Boolean) = b(ctx).setIncludeSynthetic(included)

    fun getRecentQuestionIds(ctx: Context?): List<String> = b(ctx).getRecentQuestionIds()
    fun pushRecentQuestionId(ctx: Context?, id: String, maxSize: Int) =
        b(ctx).pushRecentQuestionId(id, maxSize)

    fun getStats(ctx: Context?): Map<String, QStat> = b(ctx).getStats()
    fun recordShown(ctx: Context?, id: String) = b(ctx).recordShown(id)
    fun recordAnswer(ctx: Context?, id: String, correct: Boolean) = b(ctx).recordAnswer(id, correct)

    fun getQuizModeAnchorMs(ctx: Context?, nowMs: Long = System.currentTimeMillis()): Long =
        b(ctx).getQuizModeAnchorMs(nowMs)
}
