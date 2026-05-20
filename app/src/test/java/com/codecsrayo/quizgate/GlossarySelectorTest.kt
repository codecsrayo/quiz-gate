package com.codecsrayo.quizgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GlossarySelectorTest {

    private fun term(symbol: String, domains: List<String> = emptyList()) = GlossaryTerm(
        symbol = symbol,
        name = "",
        category = "",
        desc = mapOf("es" to "d"),
        domains = domains,
    )

    @Test
    fun picks_from_biased_pool_when_failing_domains_overlap() {
        val pool = listOf(
            term("iam", domains = listOf("security")),
            term("ec2"),
            term("s3"),
            term("vpc"),
        )
        val counts = HashMap<String, Int>()
        repeat(500) { seed ->
            val pick = GlossarySelector.pick(
                terms = pool,
                recentSymbols = emptySet(),
                failingDomains = setOf("security"),
                random = Random(seed.toLong()),
            )
            counts.merge(pick.symbol, 1) { a, _ -> a + 1 }
        }
        val biased = counts["iam"] ?: 0
        val others = counts.entries.filter { it.key != "iam" }.sumOf { it.value }
        assertTrue(
            "biased term (iam=$biased) should outweigh neutral pool (others=$others)",
            biased > others,
        )
        assertTrue("iam should dominate (>50%): $biased/500", biased > 250)
    }

    @Test
    fun falls_back_to_uniform_when_no_biased_terms_exist() {
        val pool = listOf(term("a"), term("b"), term("c"))
        val seen = mutableSetOf<String>()
        repeat(200) { seed ->
            val pick = GlossarySelector.pick(
                terms = pool,
                recentSymbols = emptySet(),
                failingDomains = setOf("security"),
                random = Random(seed.toLong()),
            )
            seen += pick.symbol
        }
        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun falls_back_to_uniform_when_failing_domains_is_empty() {
        val pool = listOf(
            term("a", domains = listOf("security")),
            term("b", domains = listOf("billing")),
            term("c", domains = listOf("technology")),
        )
        val counts = HashMap<String, Int>()
        repeat(300) { seed ->
            val pick = GlossarySelector.pick(
                terms = pool,
                recentSymbols = emptySet(),
                failingDomains = emptySet(),
                random = Random(seed.toLong()),
            )
            counts.merge(pick.symbol, 1) { a, _ -> a + 1 }
        }
        assertEquals(setOf("a", "b", "c"), counts.keys)
        // Roughly uniform — every term should have meaningful representation.
        counts.values.forEach { assertTrue("count $it should be > 30 in 300 trials", it > 30) }
    }

    @Test
    fun excludes_recent_symbols_when_other_terms_exist() {
        val pool = listOf(term("a"), term("b"), term("c"))
        val seen = mutableSetOf<String>()
        repeat(200) { seed ->
            val pick = GlossarySelector.pick(
                terms = pool,
                recentSymbols = setOf("a"),
                failingDomains = emptySet(),
                random = Random(seed.toLong()),
            )
            seen += pick.symbol
        }
        assertFalse("'a' is recent and should never be picked", "a" in seen)
        assertTrue("b" in seen)
        assertTrue("c" in seen)
    }

    @Test
    fun recents_fallback_when_all_terms_are_recent() {
        val pool = listOf(term("a"), term("b"))
        val pick = GlossarySelector.pick(
            terms = pool,
            recentSymbols = setOf("a", "b"),
            failingDomains = emptySet(),
            random = Random(0),
        )
        assertTrue(pick.symbol in setOf("a", "b"))
    }

    @Test
    fun bias_roll_above_ratio_picks_uniformly() {
        // biasRatio=0.0f -> roll never succeeds; even with a biased candidate,
        // neutral terms must still appear in the output distribution.
        val pool = listOf(
            term("biased", domains = listOf("security")),
            term("neutral-a"),
            term("neutral-b"),
        )
        val seen = mutableSetOf<String>()
        repeat(200) { seed ->
            val pick = GlossarySelector.pick(
                terms = pool,
                recentSymbols = emptySet(),
                failingDomains = setOf("security"),
                biasRatio = 0.0f,
                random = Random(seed.toLong()),
            )
            seen += pick.symbol
        }
        // The biased term is still in the candidate pool, so it can appear,
        // but neutral terms MUST appear since bias never triggers.
        assertTrue("neutral-a should be picked at least once", "neutral-a" in seen)
        assertTrue("neutral-b should be picked at least once", "neutral-b" in seen)
    }

    @Test
    fun throws_on_empty_pool() {
        assertThrows(IllegalArgumentException::class.java) {
            GlossarySelector.pick(emptyList(), emptySet(), emptySet())
        }
    }
}
