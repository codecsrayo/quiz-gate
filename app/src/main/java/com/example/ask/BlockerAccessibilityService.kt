package com.example.ask

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BlockerAccessibilityService : AccessibilityService() {

    @Volatile private var lastForegroundPkg: String? = null
    @Volatile private var lastLaunchMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var pendingTimeout: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected")
        WatchdogService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (isSystemPackage(pkg)) return

        val prev = lastForegroundPkg
        if (pkg == prev) return
        lastForegroundPkg = pkg

        val blocked = Prefs.getBlockedPackages(this)

        // Leaving a blocked app: record last-seen, pause in-app timer.
        if (prev != null && prev != pkg && prev in blocked) {
            Prefs.touchLastSeen(this, prev)
            cancelTimeout()
        }

        if (pkg == packageName) return

        if (pkg !in blocked) {
            cancelTimeout()
            return
        }

        // Entering a blocked app via fresh unlock from quiz
        if (Prefs.consumePendingUnlock(this, pkg)) {
            Log.i(TAG, "consumed pending unlock for $pkg")
            Prefs.touchLastSeen(this, pkg)
            Prefs.setSessionStart(this, pkg, System.currentTimeMillis())
            scheduleInAppTimeout(pkg)
            return
        }

        // Re-entering within session window
        if (Prefs.isWithinSessionWindow(this, pkg)) {
            Prefs.touchLastSeen(this, pkg)
            val maxMs = Prefs.getMaxInAppMs(this)
            val start = Prefs.getSessionStart(this, pkg)
            if (maxMs > 0 && start > 0L && System.currentTimeMillis() - start >= maxMs) {
                Log.i(TAG, "in-app limit exceeded on re-entry to $pkg → quiz")
                triggerQuiz(pkg, resetSession = true)
                return
            }
            scheduleInAppTimeout(pkg)
            return
        }

        // First entry / expired session: fresh quiz
        val now = System.currentTimeMillis()
        if (now - lastLaunchMs < 1500L) {
            Log.i(TAG, "debounced launch for $pkg")
            return
        }
        lastLaunchMs = now
        Log.i(TAG, "blocking $pkg → launching QuizActivity")
        triggerQuiz(pkg, resetSession = true)
    }

    private fun scheduleInAppTimeout(pkg: String) {
        cancelTimeout()
        val maxMs = Prefs.getMaxInAppMs(this)
        if (maxMs <= 0) return
        val start = Prefs.getSessionStart(this, pkg).takeIf { it > 0L }
            ?: System.currentTimeMillis().also { Prefs.setSessionStart(this, pkg, it) }
        val deadline = start + maxMs
        val delay = deadline - System.currentTimeMillis()
        val r = Runnable {
            if (lastForegroundPkg == pkg) {
                Log.i(TAG, "in-app time limit reached for $pkg → quiz")
                triggerQuiz(pkg, resetSession = true)
            }
        }
        pendingTimeout = r
        if (delay <= 0) handler.post(r) else handler.postDelayed(r, delay)
    }

    private fun cancelTimeout() {
        pendingTimeout?.let { handler.removeCallbacks(it) }
        pendingTimeout = null
    }

    private fun triggerQuiz(pkg: String, resetSession: Boolean) {
        if (resetSession) {
            Prefs.clearSessionStart(this, pkg)
            Prefs.clearLastSeen(this, pkg)
        }
        cancelTimeout()
        lastLaunchMs = System.currentTimeMillis()
        Prefs.setLastBlockedPackage(this, pkg)
        WatchdogService.start(this)
        val intent = Intent(this, QuizActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(QuizActivity.EXTRA_TRIGGER_PKG, pkg)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.e(TAG, "startActivity QuizActivity failed", it) }
    }

    private fun isSystemPackage(pkg: String): Boolean {
        if (pkg == "com.android.systemui") return true
        if (pkg.startsWith("com.miui.systemui")) return true
        if (pkg.endsWith(".ime")) return true
        return false
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        cancelTimeout()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QuizGate"
    }
}
