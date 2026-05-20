package com.codecsrayo.quizgate

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizModeTest {

    private val anchor = 1_700_000_000_000L
    private val hourMs = 3_600_000L

    @Test
    fun at_anchor_is_novedad() {
        assertEquals(QuizMode.Mode.Novedad, QuizMode.current(anchor, anchor))
    }

    @Test
    fun thirty_minutes_in_still_novedad() {
        assertEquals(QuizMode.Mode.Novedad, QuizMode.current(anchor, anchor + hourMs / 2))
    }

    @Test
    fun exactly_one_hour_in_flips_to_refuerzo() {
        assertEquals(QuizMode.Mode.Refuerzo, QuizMode.current(anchor, anchor + hourMs))
    }

    @Test
    fun two_hours_in_back_to_novedad() {
        assertEquals(QuizMode.Mode.Novedad, QuizMode.current(anchor, anchor + 2 * hourMs))
    }

    @Test
    fun three_hours_in_refuerzo_again() {
        assertEquals(QuizMode.Mode.Refuerzo, QuizMode.current(anchor, anchor + 3 * hourMs))
    }

    @Test
    fun clock_going_backwards_is_treated_as_novedad() {
        assertEquals(QuizMode.Mode.Novedad, QuizMode.current(anchor, anchor - hourMs))
        assertEquals(QuizMode.Mode.Novedad, QuizMode.current(anchor, anchor - 5 * hourMs))
    }
}
