package com.codecsrayo.quizgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizDomainStatsTest {

    private fun q(id: String, domain: String) = Question(
        id = id,
        domain = domain,
        domainLabel = emptyMap(),
        questionType = "multiple-choice",
        scenarioBased = false,
        synthetic = false,
        q = emptyMap(),
        options = emptyMap(),
        answer = 0,
        answers = emptyList(),
        explanation = emptyMap(),
        tip = emptyMap(),
    )

    @Test
    fun empty_stats_returns_empty_set() {
        val out = QuizDomainStats.failingDomains(
            stats = emptyMap(),
            questionsById = mapOf("a" to q("a", "security")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun filters_out_questions_below_min_shown() {
        // 100% wrong but only 2 attempts -> below default minShown=3, must be skipped.
        val out = QuizDomainStats.failingDomains(
            stats = mapOf("a" to Prefs.QStat(shown = 2, correct = 0, wrong = 2)),
            questionsById = mapOf("a" to q("a", "security")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun filters_out_questions_at_or_below_failure_threshold() {
        // shown=10, wrong=5 -> rate=0.5 exactly, threshold is `>` not `>=`.
        val out = QuizDomainStats.failingDomains(
            stats = mapOf("a" to Prefs.QStat(shown = 10, correct = 5, wrong = 5)),
            questionsById = mapOf("a" to q("a", "security")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun includes_questions_above_threshold() {
        val out = QuizDomainStats.failingDomains(
            stats = mapOf("a" to Prefs.QStat(shown = 10, correct = 4, wrong = 6)),
            questionsById = mapOf("a" to q("a", "security")),
        )
        assertEquals(setOf("security"), out)
    }

    @Test
    fun deduplicates_domains_across_multiple_failing_questions() {
        val out = QuizDomainStats.failingDomains(
            stats = mapOf(
                "a" to Prefs.QStat(shown = 10, correct = 0, wrong = 10),
                "b" to Prefs.QStat(shown = 5, correct = 1, wrong = 4),
            ),
            questionsById = mapOf(
                "a" to q("a", "security"),
                "b" to q("b", "security"),
            ),
        )
        assertEquals(setOf("security"), out)
        assertEquals(1, out.size)
    }

    @Test
    fun skips_stats_for_unknown_question_ids() {
        val out = QuizDomainStats.failingDomains(
            stats = mapOf("ghost" to Prefs.QStat(shown = 10, correct = 0, wrong = 10)),
            questionsById = emptyMap(),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun respects_custom_thresholds() {
        // minShown=1, threshold=0.0f -> shown=1/wrong=1 is included (1.0 > 0.0).
        val out = QuizDomainStats.failingDomains(
            stats = mapOf("a" to Prefs.QStat(shown = 1, correct = 0, wrong = 1)),
            questionsById = mapOf("a" to q("a", "billing")),
            minShown = 1,
            failureRateThreshold = 0.0f,
        )
        assertEquals(setOf("billing"), out)
    }
}
