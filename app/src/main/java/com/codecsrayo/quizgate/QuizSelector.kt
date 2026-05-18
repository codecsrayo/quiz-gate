package com.codecsrayo.quizgate

import kotlin.random.Random

/**
 * Pure selection logic — no Android dependencies, so it can be unit-tested on the host JVM.
 * `QuizActivity.pickRandom` is the thin wrapper that reads Prefs and dispatches the result.
 */
object QuizSelector {

    sealed interface Result {
        data object NoCache : Result
        data object NoSourceSelected : Result
        data object NoSourceQuestions : Result
        data object NoDomain : Result
        data class Picked(
            val question: Question,
            val permutation: List<Int>,
        ) : Result
    }

    fun pick(
        pool: List<Question>,
        includeOfficial: Boolean,
        includeSynthetic: Boolean,
        enabledDomains: Set<String>?,
        recentIds: Set<String>,
        stats: Map<String, Prefs.QStat>,
        random: Random = Random.Default,
    ): Result {
        if (pool.isEmpty()) return Result.NoCache
        if (!includeOfficial && !includeSynthetic) return Result.NoSourceSelected

        val bySource = pool.filter { q ->
            (q.synthetic && includeSynthetic) || (!q.synthetic && includeOfficial)
        }
        if (bySource.isEmpty()) return Result.NoSourceQuestions

        val byDomain = if (enabledDomains == null) bySource
            else bySource.filter { it.domain in enabledDomains }
        if (byDomain.isEmpty()) return Result.NoDomain

        val candidates = byDomain.filterNot { it.id in recentIds }
            .ifEmpty { byDomain }

        val totalWeight = candidates.sumOf { weight(it, stats) }
        var remaining = random.nextLong(totalWeight)
        val chosen = candidates.first {
            remaining -= weight(it, stats)
            remaining < 0L
        }
        val nOpts = chosen.options.values.firstOrNull()?.size ?: 0
        val perm = (0 until nOpts).shuffled(random)
        return Result.Picked(chosen, perm)
    }

    private fun weight(q: Question, stats: Map<String, Prefs.QStat>): Long {
        val s = stats[q.id]
        val wrong = s?.wrong ?: 0
        val correct = s?.correct ?: 0
        return (1L + 2L * wrong - correct).coerceAtLeast(1L)
    }
}
