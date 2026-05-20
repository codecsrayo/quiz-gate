package com.codecsrayo.quizgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizSelectorTest {

    private fun q(
        id: String,
        domain: String = "d1",
        synthetic: Boolean = false,
        nOpts: Int = 4,
        answer: Int = 0,
        answers: List<Int> = emptyList(),
        type: String = "multiple-choice",
    ) = Question(
        id = id,
        domain = domain,
        domainLabel = emptyMap(),
        questionType = type,
        scenarioBased = false,
        synthetic = synthetic,
        q = mapOf("es" to "?", "en" to "?"),
        options = mapOf("es" to List(nOpts) { "o$it" }, "en" to List(nOpts) { "o$it" }),
        answer = answer,
        answers = answers,
        explanation = emptyMap(),
        tip = emptyMap(),
    )

    @Test
    fun empty_pool_returns_no_cache() {
        val r = QuizSelector.pick(
            pool = emptyList(),
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
        )
        assertEquals(QuizSelector.Result.NoCache, r)
    }

    @Test
    fun no_sources_selected_returns_error() {
        val r = QuizSelector.pick(
            pool = listOf(q("a")),
            includeOfficial = false,
            includeSynthetic = false,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
        )
        assertEquals(QuizSelector.Result.NoSourceSelected, r)
    }

    @Test
    fun official_only_excludes_synthetic() {
        val pool = listOf(q("a", synthetic = false), q("b", synthetic = true))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = false,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            random = Random(0),
        ) as QuizSelector.Result.Picked
        assertEquals("a", r.question.id)
    }

    @Test
    fun synthetic_only_excludes_official() {
        val pool = listOf(q("a", synthetic = false), q("b", synthetic = true))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = false,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            random = Random(0),
        ) as QuizSelector.Result.Picked
        assertEquals("b", r.question.id)
    }

    @Test
    fun source_filter_empties_pool_returns_no_source_questions() {
        // Pool has only official; user asked synthetic only -> NoSourceQuestions
        val pool = listOf(q("a", synthetic = false))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = false,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
        )
        assertEquals(QuizSelector.Result.NoSourceQuestions, r)
    }

    @Test
    fun domain_filter_picks_only_enabled_domains() {
        val pool = listOf(q("a", domain = "d1"), q("b", domain = "d2"), q("c", domain = "d3"))
        repeat(20) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = setOf("d2"),
                recentIds = emptySet(),
                stats = emptyMap(),
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            assertEquals("b", r.question.id)
        }
    }

    @Test
    fun all_domains_excluded_returns_no_domain() {
        val pool = listOf(q("a", domain = "d1"))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = setOf("dX"),
            recentIds = emptySet(),
            stats = emptyMap(),
        )
        assertEquals(QuizSelector.Result.NoDomain, r)
    }

    @Test
    fun null_enabled_domains_treats_all_as_enabled() {
        val pool = listOf(q("a", domain = "d1"), q("b", domain = "d2"))
        val seen = mutableSetOf<String>()
        repeat(100) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = emptyMap(),
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            seen += r.question.id
        }
        assertEquals(setOf("a", "b"), seen)
    }

    @Test
    fun recent_questions_are_excluded_when_other_candidates_exist() {
        val pool = listOf(q("a"), q("b"), q("c"))
        val seen = mutableSetOf<String>()
        repeat(100) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = setOf("a"),
                stats = emptyMap(),
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            seen += r.question.id
        }
        assertFalse("'a' is recent and should never be picked", "a" in seen)
        assertTrue("b" in seen)
        assertTrue("c" in seen)
    }

    @Test
    fun recent_fallback_when_filter_would_empty_candidates() {
        // Every question is "recent" — must fall back to the byDomain set, not error.
        val pool = listOf(q("a"), q("b"))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = setOf("a", "b"),
            stats = emptyMap(),
            random = Random(0),
        )
        assertTrue(r is QuizSelector.Result.Picked)
        assertTrue((r as QuizSelector.Result.Picked).question.id in setOf("a", "b"))
    }

    @Test
    fun weight_favors_frequently_wrong_questions() {
        val pool = listOf(q("easy"), q("hard"))
        // easy: weight = max(1, 1 + 0 - 8) = 1
        // hard: weight = max(1, 1 + 16 - 0) = 17 -> picked ~17x more often
        val stats = mapOf(
            "easy" to Prefs.QStat(shown = 8, correct = 8, wrong = 0),
            "hard" to Prefs.QStat(shown = 8, correct = 0, wrong = 8),
        )
        val counts = HashMap<String, Int>()
        repeat(2000) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = stats,
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            counts.merge(r.question.id, 1) { a, _ -> a + 1 }
        }
        val hard = counts["hard"] ?: 0
        val easy = counts["easy"] ?: 0
        assertTrue("hard ($hard) should be picked far more than easy ($easy)", hard > easy * 5)
    }

    @Test
    fun weight_floor_is_one_even_for_mastered_questions() {
        // A question answered correctly many times still has weight >= 1, so it can still appear.
        val pool = listOf(q("solo"))
        val stats = mapOf("solo" to Prefs.QStat(shown = 100, correct = 100, wrong = 0))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = stats,
            random = Random(0),
        )
        assertTrue(r is QuizSelector.Result.Picked)
        assertEquals("solo", (r as QuizSelector.Result.Picked).question.id)
    }

    @Test
    fun permutation_size_matches_option_count() {
        val pool = listOf(q("a", nOpts = 5))
        repeat(20) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = emptyMap(),
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            assertEquals(5, r.permutation.size)
        }
    }

    @Test
    fun permutation_is_a_valid_bijection_of_indices() {
        val pool = listOf(q("a", nOpts = 6))
        repeat(50) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = emptyMap(),
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            assertEquals((0 until 6).toSet(), r.permutation.toSet())
            assertEquals(6, r.permutation.distinct().size)
        }
    }

    @Test
    fun permutation_is_not_always_identity() {
        val pool = listOf(q("a", nOpts = 5))
        var sawShuffled = false
        repeat(50) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = emptyMap(),
                    random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            if (r.permutation != (0 until 5).toList()) sawShuffled = true
        }
        assertTrue("permutation should differ from identity at least once", sawShuffled)
    }

    @Test
    fun mapping_display_to_original_round_trips_single_response() {
        // Single-correct: original answer index = 2. Find display slot that maps to 2,
        // and confirm mapping back yields {2}.
        val question = q("single", nOpts = 4, answer = 2)
        val r = QuizSelector.pick(
            pool = listOf(question),
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            random = Random(7),
        ) as QuizSelector.Result.Picked

        val correct = question.correctAnswers()
        val displayIdxForCorrect = r.permutation.indexOf(2)
        assertNotNull(displayIdxForCorrect)
        val selectedDisplay = setOf(displayIdxForCorrect)
        val mappedBack = selectedDisplay.map { r.permutation[it] }.toSet()
        assertEquals(correct, mappedBack)
    }

    @Test
    fun mapping_display_to_original_round_trips_multi_response() {
        val question = q(
            "multi",
            nOpts = 5,
            answers = listOf(1, 3),
            type = "multiple-response",
        )
        val r = QuizSelector.pick(
            pool = listOf(question),
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            random = Random(42),
        ) as QuizSelector.Result.Picked

        val correct = question.correctAnswers() // {1, 3}
        val selectedDisplay = correct.map { r.permutation.indexOf(it) }.toSet()
        val mappedBack = selectedDisplay.map { r.permutation[it] }.toSet()
        assertEquals(correct, mappedBack)
    }

    @Test
    fun mapping_wrong_display_selection_does_not_round_trip_to_correct() {
        val question = q("single", nOpts = 4, answer = 0)
        val r = QuizSelector.pick(
            pool = listOf(question),
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            random = Random(3),
        ) as QuizSelector.Result.Picked

        val correct = question.correctAnswers() // {0}
        val correctDisplay = r.permutation.indexOf(0)
        // Pick any display index that's NOT the correct one
        val wrongDisplay = (0 until 4).first { it != correctDisplay }
        val mappedBack = setOf(r.permutation[wrongDisplay])
        assertFalse(mappedBack == correct)
    }

    @Test
    fun novedad_mode_picks_only_unseen_questions() {
        val pool = listOf(q("a"), q("b"))
        val stats = mapOf("b" to Prefs.QStat(shown = 5, correct = 2, wrong = 3))
        repeat(50) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = stats,
                mode = QuizMode.Mode.Novedad,
                random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            assertEquals("a", r.question.id)
        }
    }

    @Test
    fun refuerzo_mode_picks_only_seen_questions() {
        val pool = listOf(q("a"), q("b"))
        val stats = mapOf("b" to Prefs.QStat(shown = 5, correct = 2, wrong = 3))
        repeat(50) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = emptySet(),
                stats = stats,
                mode = QuizMode.Mode.Refuerzo,
                random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            assertEquals("b", r.question.id)
        }
    }

    @Test
    fun novedad_mode_falls_back_to_refuerzo_when_no_unseen_left() {
        val pool = listOf(q("a"), q("b"))
        val stats = mapOf(
            "a" to Prefs.QStat(shown = 3, correct = 1, wrong = 2),
            "b" to Prefs.QStat(shown = 5, correct = 2, wrong = 3),
        )
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = stats,
            mode = QuizMode.Mode.Novedad,
            random = Random(0),
        )
        assertTrue(r is QuizSelector.Result.Picked)
        assertTrue((r as QuizSelector.Result.Picked).question.id in setOf("a", "b"))
    }

    @Test
    fun refuerzo_mode_falls_back_to_novedad_when_stats_empty() {
        val pool = listOf(q("a"), q("b"))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            mode = QuizMode.Mode.Refuerzo,
            random = Random(0),
        )
        assertTrue(r is QuizSelector.Result.Picked)
        assertTrue((r as QuizSelector.Result.Picked).question.id in setOf("a", "b"))
    }

    @Test
    fun default_mode_parameter_keeps_legacy_behavior_when_no_stats() {
        // No mode specified -> defaults to Refuerzo. With empty stats, the refuerzo
        // bucket is empty so it falls back to novedad (the whole pool).
        val pool = listOf(q("a"), q("b"))
        val r = QuizSelector.pick(
            pool = pool,
            includeOfficial = true,
            includeSynthetic = true,
            enabledDomains = null,
            recentIds = emptySet(),
            stats = emptyMap(),
            random = Random(0),
        )
        assertTrue(r is QuizSelector.Result.Picked)
        assertTrue((r as QuizSelector.Result.Picked).question.id in setOf("a", "b"))
    }

    @Test
    fun mode_partition_respects_recents_exclusion() {
        val pool = listOf(q("seen-a"), q("seen-b"))
        val stats = mapOf(
            "seen-a" to Prefs.QStat(shown = 1, correct = 0, wrong = 1),
            "seen-b" to Prefs.QStat(shown = 1, correct = 0, wrong = 1),
        )
        repeat(50) { seed ->
            val r = QuizSelector.pick(
                pool = pool,
                includeOfficial = true,
                includeSynthetic = true,
                enabledDomains = null,
                recentIds = setOf("seen-a"),
                stats = stats,
                mode = QuizMode.Mode.Refuerzo,
                random = Random(seed.toLong()),
            ) as QuizSelector.Result.Picked
            assertEquals("seen-b", r.question.id)
        }
    }
}
