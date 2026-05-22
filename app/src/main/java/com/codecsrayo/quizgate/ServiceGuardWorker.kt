package com.codecsrayo.quizgate

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic guard that detects when the blocking gate has silently fallen over.
 *
 * Unlike [GlossaryPushWorker] this runs unconditionally — it does not depend on
 * any user setting — so it keeps watching even when the glossary push is off.
 * Every 15 min it checks the accessibility heartbeat and, if the gate is down,
 * raises a high-priority notification so the user can re-enable the service.
 */
class ServiceGuardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // Best-effort: if the process is alive but the watchdog FGS was killed,
        // try to bring it back. WatchdogService.start() swallows the
        // ForegroundServiceStartNotAllowedException thrown on platforms that
        // forbid a background FGS start, so this is safe to attempt blindly.
        WatchdogService.start(ctx)

        val down = ServiceGuard.isAccessibilityDown(
            toggleEnabled = Permissions.isAccessibilityEnabled(ctx),
            heartbeatMs = Prefs.getAccessibilityHeartbeatMs(ctx),
            nowMs = System.currentTimeMillis(),
        )
        if (down) {
            Log.w(TAG, "Accessibility gate is down — alerting user")
            ServiceAlertNotification.post(ctx)
        } else {
            ServiceAlertNotification.cancel(ctx)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ServiceGuard"
        private const val WORK_NAME = "service_guard"
        private const val INTERVAL_MINUTES = 15L

        fun schedule(ctx: Context) {
            val app = ctx.applicationContext
            // Seed a heartbeat so the staleness clock starts now instead of from
            // epoch 0 (which would read as instantly stale on the first check).
            if (Prefs.getAccessibilityHeartbeatMs(app) <= 0L) {
                Prefs.setAccessibilityHeartbeatMs(app, System.currentTimeMillis())
            }
            val request = PeriodicWorkRequestBuilder<ServiceGuardWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
