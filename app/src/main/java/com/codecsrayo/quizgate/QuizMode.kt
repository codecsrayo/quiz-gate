package com.codecsrayo.quizgate

/**
 * Hourly rotation between two study modes:
 *   - Novedad: prefer unseen questions (`stats[id].shown == 0`).
 *   - Refuerzo: prefer already-seen questions.
 *
 * Rotation is anchor-based and stateless: given a fixed `anchorMs` (persisted
 * once on first launch) and the current wall clock, the active mode is a pure
 * function of elapsed full hours mod 2. No periodic writes, no timers.
 */
object QuizMode {

    enum class Mode { Novedad, Refuerzo }

    /**
     * Returns the active mode for [nowMs] given the persisted [anchorMs].
     * Clock going backwards (DST, NTP correction) is tolerated — the mode for
     * `nowMs < anchorMs` is treated as the elapsed-zero case (Novedad).
     */
    fun current(anchorMs: Long, nowMs: Long): Mode {
        val elapsedHours = ((nowMs - anchorMs).coerceAtLeast(0L)) / 3_600_000L
        return if (elapsedHours % 2L == 0L) Mode.Novedad else Mode.Refuerzo
    }
}
