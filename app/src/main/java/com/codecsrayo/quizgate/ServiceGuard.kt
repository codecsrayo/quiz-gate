package com.codecsrayo.quizgate

/**
 * Pure decision logic for [ServiceGuardWorker].
 *
 * HyperOS/MIUI kills the app process (it killed it at 00:19 on a charging night,
 * not under low battery) and frequently does NOT rebind the accessibility
 * service afterwards: the Settings toggle stays ON but the service never runs
 * again — it lands in AccessibilityManagerService's "crashed services" set.
 *
 * The Settings toggle alone therefore can't tell "enabled and running" from
 * "enabled on paper but dead". The heartbeat — refreshed only by a live
 * [BlockerAccessibilityService] — closes that gap.
 */
object ServiceGuard {

    /** A heartbeat older than this means the service is no longer running. */
    const val STALENESS_THRESHOLD_MS = 35 * 60_000L

    /**
     * @param toggleEnabled whether the service is enabled in Accessibility settings
     * @param heartbeatMs   last heartbeat epoch-ms (0 = none recorded yet)
     * @param nowMs         current epoch-ms
     */
    fun isAccessibilityDown(
        toggleEnabled: Boolean,
        heartbeatMs: Long,
        nowMs: Long,
        stalenessThresholdMs: Long = STALENESS_THRESHOLD_MS,
    ): Boolean {
        // Toggle off — gate is down regardless of any past heartbeat.
        if (!toggleEnabled) return true
        // No heartbeat recorded yet (fresh install / pre-seed window): give the
        // service the benefit of the doubt. ServiceGuardWorker.schedule() seeds
        // a heartbeat, so this branch only covers a tiny startup window.
        if (heartbeatMs <= 0L) return false
        return nowMs - heartbeatMs >= stalenessThresholdMs
    }
}
