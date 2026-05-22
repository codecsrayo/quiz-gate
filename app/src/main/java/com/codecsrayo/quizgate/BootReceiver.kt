package com.codecsrayo.quizgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms blocking after a reboot.
 *
 * WorkManager restores its own periodic jobs after boot, but the watchdog
 * foreground service is not — and previously it was only ever started from
 * inside [BlockerAccessibilityService], so a reboot left it dead until the
 * service happened to reconnect. Starting it here (BOOT_COMPLETED is an allowed
 * background-FGS-start window) and re-seeding the guard worker closes that gap.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val ctx = context.applicationContext
                WatchdogService.start(ctx)
                ServiceGuardWorker.schedule(ctx)
                GlossaryPushWorker.applyFromPrefs(ctx)
            }
        }
    }
}
