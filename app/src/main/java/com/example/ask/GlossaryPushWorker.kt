package com.example.ask

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class GlossaryPushWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = GlossaryRepository(ctx)

        var terms = repo.loadCached()
        if (terms.isEmpty()) {
            val r = repo.refreshFromNetwork()
            if (r.isFailure) return Result.retry()
            terms = repo.loadCached()
            if (terms.isEmpty()) return Result.retry()
        }

        val recent = Prefs.getRecentGlossaryIds(ctx).toSet()
        val candidates = terms.filterNot { it.symbol in recent }.ifEmpty { terms }
        val chosen = candidates.random()

        val maxRecent = (terms.size / 2).coerceIn(5, 50)
        Prefs.pushRecentGlossaryId(ctx, chosen.symbol, maxRecent)

        GlossaryNotifications.postTerm(ctx, chosen, Prefs.getLang(ctx))
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "glossary_push"

        fun schedule(ctx: Context, intervalMinutes: Int) {
            val safe = intervalMinutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<GlossaryPushWorker>(safe, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(WORK_NAME)
        }

        fun applyFromPrefs(ctx: Context) {
            if (Prefs.isGlossaryPushEnabled(ctx)) {
                schedule(ctx, Prefs.getGlossaryPushIntervalMin(ctx))
            } else {
                cancel(ctx)
            }
        }
    }
}
