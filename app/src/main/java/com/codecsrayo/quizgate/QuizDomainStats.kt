package com.codecsrayo.quizgate

/**
 * Derives the set of CLF-C02 domain codes where the user has demonstrated
 * weakness, used by [GlossarySelector] to bias glossary push notifications
 * toward reinforcement of those domains.
 *
 * A domain is "failing" when at least one question in it has been shown
 * enough times to be a real signal ([minShown]) and the user's wrong-rate
 * on that question exceeds [failureRateThreshold]. The shown gate avoids
 * treating a single early wrong answer as a domain weakness.
 */
object QuizDomainStats {

    const val DEFAULT_MIN_SHOWN = 3
    const val DEFAULT_FAILURE_RATE_THRESHOLD = 0.5f

    fun failingDomains(
        stats: Map<String, Prefs.QStat>,
        questionsById: Map<String, Question>,
        minShown: Int = DEFAULT_MIN_SHOWN,
        failureRateThreshold: Float = DEFAULT_FAILURE_RATE_THRESHOLD,
    ): Set<String> = stats.entries
        .filter { (_, s) -> s.shown >= minShown && s.wrong.toFloat() / s.shown > failureRateThreshold }
        .mapNotNull { questionsById[it.key]?.domain }
        .toSet()
}
