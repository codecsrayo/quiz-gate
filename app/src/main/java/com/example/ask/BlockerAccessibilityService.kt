package com.example.ask

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BlockerAccessibilityService : AccessibilityService() {

    @Volatile private var lastForegroundPkg: String? = null
    @Volatile private var lastLaunchMs: Long = 0L

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

        // If we are LEAVING a blocked app (or quiz activity), record its last-seen time
        // so brief excursions don't re-trigger the quiz on return.
        if (prev != null && prev != pkg && prev in blocked) {
            Prefs.touchLastSeen(this, prev)
        }

        if (pkg == packageName) return

        if (pkg !in blocked) return

        if (Prefs.consumePendingUnlock(this, pkg)) {
            Log.i(TAG, "consumed pending unlock for $pkg")
            Prefs.touchLastSeen(this, pkg)
            return
        }

        if (Prefs.isWithinSessionWindow(this, pkg)) {
            Log.i(TAG, "in active session for $pkg, skipping quiz")
            Prefs.touchLastSeen(this, pkg)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastLaunchMs < 1500L) {
            Log.i(TAG, "debounced launch for $pkg")
            return
        }
        lastLaunchMs = now

        Log.i(TAG, "blocking $pkg → launching QuizActivity")
        Prefs.setLastBlockedPackage(this, pkg)
        WatchdogService.start(this)

        val intent = Intent(this, QuizActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
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

    companion object {
        private const val TAG = "QuizGate"
    }
}
