package com.codecsrayo.quizgate

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

private val IN_APP_TIMER_EXEMPT = setOf(
    "com.whatsapp",
    "com.whatsapp.w4b",
)

private val TRANSIENT_PKGS = setOf(
    "com.android.systemui",
    "com.miui.home",
    "com.miui.launcher",
    "com.mi.android.globallauncher",
    "com.google.android.apps.nexuslauncher",
    "com.android.launcher",
    "com.android.launcher3",
    // Share-sheet / intent resolvers (treat like launcher: pass-through, don't reset session)
    "com.android.intentresolver",
    "com.google.android.intentresolver",
    "com.miui.intentresolver",
)

// Keyboards that don't follow the `.ime` suffix convention. The InputMethodManager
// query at service start is the primary source; this list is a fallback for devices
// where the IME isn't listed as "enabled" yet or for IMEs installed mid-session.
private val KNOWN_IME_PKGS = setOf(
    "com.google.android.inputmethod.latin",      // Gboard
    "com.google.android.inputmethod.pinyin",     // Google Pinyin
    "com.google.android.inputmethod.japanese",   // Gboard JP
    "com.google.android.inputmethod.korean",     // Gboard KR
    "com.touchtype.swiftkey",                    // Microsoft SwiftKey
    "com.touchtype.swiftkey.beta",
    "com.samsung.android.honeyboard",            // Samsung Keyboard
    "com.sec.android.inputmethod",               // Samsung legacy
    "com.miui.securityinputmethod",              // MIUI secure input
    "com.iflytek.inputmethod.miui",              // HyperOS keyboard (iFlytek-based)
    "com.baidu.input_mi",                        // Baidu (MIUI variant)
    "com.sohu.inputmethod.sogou.xiaomi",         // Sogou (Xiaomi variant)
    "com.cootek.smartinputv5",                   // TouchPal
    "com.syntellia.fleksy.keyboard",             // Fleksy
    "com.grammarly.android.keyboard",            // Grammarly
    "com.menny.android.anysoftkeyboard",         // AnySoftKeyboard
    "com.klye.ime.latin",                        // Multiling O
    "com.lge.ime",                               // LG
    "kik.android.kikkeyboard",                   // Kika
)

// Activity classes that indicate the blocked app was opened to receive an external
// intent (share-sheet target, system handler, etc.) — bypass quiz so the user can
// complete the share without interruption. Add to this list when logcat shows a new
// share-receiver className firing the quiz.
private val EXTERNAL_ENTRY_CLASSES = setOf(
    // WhatsApp share targets
    "com.whatsapp.ContactPicker",
    "com.whatsapp.contact.picker.ContactPicker",
    "com.whatsapp.gallerypicker.MediaPicker",
    // Facebook share targets
    "com.facebook.composer.shareintent.ImplicitShareIntentHandler",
    "com.facebook.composer.shareintent.ImplicitShareIntentHandlerDefaultAlias",
    "com.facebook.composer.shareintent.ShareIntentHandler",
    // Instagram share targets
    "com.instagram.share.handleractivity.ShareHandlerActivity",
    "com.instagram.direct.share.handler.DirectShareHandlerActivity",
)

private fun isTransient(pkg: String): Boolean {
    if (pkg in TRANSIENT_PKGS) return true
    if (pkg.startsWith("com.miui.systemui")) return true
    if (pkg.endsWith(".launcher") || pkg.endsWith(".launcher3")) return true
    if (pkg.endsWith(".intentresolver")) return true
    return false
}

class BlockerAccessibilityService : AccessibilityService() {

    @Volatile private var lastForegroundPkg: String? = null
    @Volatile private var lastForegroundChangeMs: Long = 0L
    @Volatile private var lastLaunchMs: Long = 0L

    // Read on every TYPE_WINDOW_STATE_CHANGED. Cached here and refreshed via the
    // SharedPreferences change listener below so we don't hit disk per event.
    @Volatile private var blockedCache: Set<String> = emptySet()

    // Union of system-enabled IMEs and the hardcoded fallback. Refreshed on connect
    // and lazily on misses so keyboard switches mid-session are picked up.
    @Volatile private var imeCache: Set<String> = KNOWN_IME_PKGS

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == Prefs.KEY_BLOCKED) {
            blockedCache = Prefs.getBlockedPackages(this)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingTimeout: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected")
        runCatching { blockedCache = Prefs.getBlockedPackages(this) }
            .onFailure { Log.e(TAG, "load blocked packages failed", it) }
        runCatching { imeCache = loadImePackages() }
            .onFailure { Log.e(TAG, "load IME packages failed", it) }
        runCatching { Prefs.registerChangeListener(this, prefsListener) }
            .onFailure { Log.e(TAG, "register prefs listener failed", it) }
        runCatching { WatchdogService.start(this) }
            .onFailure { Log.e(TAG, "start watchdog failed", it) }
    }

    private fun loadImePackages(): Set<String> {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return KNOWN_IME_PKGS
        val enabled = runCatching {
            imm.enabledInputMethodList.orEmpty().mapNotNull { it.packageName }
        }.getOrElse { emptyList() }
        return KNOWN_IME_PKGS + enabled
    }

    private fun isIme(pkg: String): Boolean {
        if (pkg.endsWith(".ime")) return true
        return pkg in imeCache
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (isSystemPackage(pkg)) return

        val prev = lastForegroundPkg
        if (pkg == prev) return
        val prevAgeMs = if (lastForegroundChangeMs > 0L)
            System.currentTimeMillis() - lastForegroundChangeMs
        else
            Long.MAX_VALUE
        lastForegroundPkg = pkg
        lastForegroundChangeMs = System.currentTimeMillis()

        val blocked = blockedCache

        // Leaving a blocked app:
        //  - To transient (launcher/systemui/recents) → keep session alive (touch lastSeen)
        //  - To another blocked app → also keep session alive: the transition is most
        //    likely a deep-link share (e.g. Facebook → WhatsApp via whatsapp://send)
        //    rather than a deliberate manual switch, which would have routed through
        //    the launcher first.
        //  - To any other real app → end session immediately
        if (prev != null && prev != pkg && prev in blocked) {
            cancelTimeout()
            if (isTransient(pkg) || pkg == packageName || pkg in blocked) {
                Prefs.touchLastSeen(this, prev)
            } else {
                Log.i(TAG, "leaving $prev → real app $pkg, ending session")
                Prefs.clearLastSeen(this, prev)
                Prefs.clearSessionStart(this, prev)
            }
        }

        if (pkg == packageName) return

        if (pkg !in blocked) {
            cancelTimeout()
            // Entered a real (non-transient, non-blocked) app: end every active
            // blocked-app session so returning to one requires a new quiz.
            if (!isTransient(pkg)) {
                for (bpkg in blocked) {
                    Prefs.clearLastSeen(this, bpkg)
                    Prefs.clearSessionStart(this, bpkg)
                }
            }
            return
        }

        // Entering any blocked app ends every OTHER blocked app's session:
        // only one blocked app can be "in active session" at a time.
        for (bpkg in blocked) {
            if (bpkg != pkg) {
                Prefs.clearLastSeen(this, bpkg)
                Prefs.clearSessionStart(this, bpkg)
            }
        }

        // If a voice/video call is active (ringing or in-call), let the user answer
        // it without going through the quiz. Refresh session so post-call return
        // also doesn't immediately trigger.
        if (isInCall()) {
            Log.i(TAG, "active call detected, bypassing quiz for $pkg")
            Prefs.touchLastSeen(this, pkg)
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
            if (pkg !in IN_APP_TIMER_EXEMPT) {
                val maxMs = Prefs.getMaxInAppMs(this)
                val start = Prefs.getSessionStart(this, pkg)
                if (maxMs > 0 && start > 0L && System.currentTimeMillis() - start >= maxMs) {
                    Log.i(TAG, "in-app limit exceeded on re-entry to $pkg → quiz")
                    triggerQuiz(pkg, resetSession = true)
                    return
                }
            }
            scheduleInAppTimeout(pkg)
            return
        }

        // External-entry windows (share-target dialogs, popups, transparent activities
        // hosting BottomSheetDialogFragments, etc.): bypass quiz and open session.
        //
        // Signal 1 — explicit match against known share-receiver activity classes.
        // Signal 2 — className reports a generic view/dialog class instead of an
        //   Activity FQN. Real activity launches report e.g.
        //   `com.whatsapp.home.ui.HomeActivity` or `com.instagram.mainactivity.InstagramMainActivity`.
        //   Share-target dialogs report the root view's class (e.g.
        //   `android.widget.FrameLayout`) because the window is a Dialog/PopupWindow,
        //   not an Activity. Matching against the framework view namespaces avoids
        //   cross-package false positives (Instagram's applicationId
        //   `com.instagram.android` doesn't match its `com.instagram.mainactivity.*`
        //   activity classes).
        val className = event.className?.toString()
        val isDialogOrPopup = className != null && (
            className.startsWith("android.widget.") ||
                className.startsWith("android.view.") ||
                className.startsWith("androidx.")
            )
        val isExternalEntry = className != null && (
            className in EXTERNAL_ENTRY_CLASSES || isDialogOrPopup
            )
        if (isExternalEntry) {
            Log.i(TAG, "external entry $className for $pkg → bypass quiz, open session")
            Prefs.touchLastSeen(this, pkg)
            Prefs.setSessionStart(this, pkg, System.currentTimeMillis())
            scheduleInAppTimeout(pkg)
            return
        }

        // Direct blocked → blocked handoff (e.g. Facebook share-link opens WhatsApp's
        // HomeActivity, indistinguishable from a launcher tap by className alone).
        // If prev is also a blocked app AND was foreground recently, the user couldn't
        // have manually navigated through launcher/recents in between — it's an
        // app-initiated handoff. Bypass quiz. The time bound prevents stale-prev cases
        // (screen-off then app launched via lock-screen notification, etc.) from
        // triggering this path.
        if (prev != null && prev in blocked && prevAgeMs < HANDOFF_WINDOW_MS) {
            Log.i(TAG, "blocked→blocked handoff $prev → $pkg (age=${prevAgeMs}ms) → bypass quiz")
            Prefs.touchLastSeen(this, pkg)
            Prefs.setSessionStart(this, pkg, System.currentTimeMillis())
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
        Log.i(TAG, "blocking $pkg (class=$className) → launching QuizActivity")
        triggerQuiz(pkg, resetSession = true)
    }

    private fun scheduleInAppTimeout(pkg: String) {
        cancelTimeout()
        if (pkg in IN_APP_TIMER_EXEMPT) return
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

    private fun isInCall(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return when (am.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_RINGTONE -> true
            else -> false
        }
    }

    private fun isSystemPackage(pkg: String): Boolean {
        if (isIme(pkg)) return true
        if (pkg == "android") return true
        if (pkg == "com.android.systemui") return true
        if (pkg.startsWith("com.miui.systemui")) return true
        if (pkg.startsWith("miui.systemui")) return true
        // Screenshot tools surface as foreground apps but are momentary overlays —
        // treating them as system avoids resetting the active blocked-app session
        // when the user captures the quiz (or any blocked app) screen.
        if (pkg == "com.miui.screenshot") return true
        if (pkg == "com.android.systemui.screenshot") return true
        if (pkg == "com.samsung.android.app.smartcapture") return true
        if (pkg.endsWith(".screenshot")) return true
        return false
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        cancelTimeout()
        runCatching { Prefs.unregisterChangeListener(this, prefsListener) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QuizGate"
        // Max age of `prev` (in ms) for a blocked→blocked transition to be considered
        // a handoff (e.g. share via deep link) rather than a return after a long pause.
        private const val HANDOFF_WINDOW_MS = 30_000L
    }
}
