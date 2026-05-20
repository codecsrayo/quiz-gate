package com.codecsrayo.quizgate

import kotlin.random.Random

/**
 * Glossary-term selection with optional domain bias. When [failingDomains]
 * is non-empty and a roll under [biasRatio] succeeds, picks from terms
 * whose [GlossaryTerm.domains] intersects the failing set; otherwise falls
 * through to uniform random on the recents-filtered candidate pool.
 *
 * Recents exclusion mirrors [QuizSelector]: if every term is "recent" the
 * filter spills back to the full term list rather than erroring.
 */
object GlossarySelector {

    const val DEFAULT_BIAS_RATIO = 0.7f

    fun pick(
        terms: List<GlossaryTerm>,
        recentSymbols: Set<String>,
        failingDomains: Set<String>,
        biasRatio: Float = DEFAULT_BIAS_RATIO,
        random: Random = Random.Default,
    ): GlossaryTerm {
        require(terms.isNotEmpty()) { "terms must not be empty" }
        val candidates = terms.filterNot { it.symbol in recentSymbols }.ifEmpty { terms }
        val biased = candidates.filter { it.domains.any(failingDomains::contains) }
        return if (biased.isNotEmpty() && random.nextFloat() < biasRatio) {
            biased.random(random)
        } else {
            candidates.random(random)
        }
    }
}
