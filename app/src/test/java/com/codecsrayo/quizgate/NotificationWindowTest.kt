package com.codecsrayo.quizgate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationWindowTest {

    private fun min(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun forward_window_includes_start_excludes_end() {
        assertTrue(NotificationWindow.isActive(min(8), min(8), min(22)))
        assertTrue(NotificationWindow.isActive(min(21, 59), min(8), min(22)))
        assertFalse(NotificationWindow.isActive(min(22), min(8), min(22)))
        assertFalse(NotificationWindow.isActive(min(7, 59), min(8), min(22)))
    }

    @Test
    fun midday_in_typical_8_to_22_window() {
        assertTrue(NotificationWindow.isActive(min(14, 30), min(8), min(22)))
    }

    @Test
    fun overnight_window_wraps_midnight() {
        // 22:00 -> 06:00 means active at 23:00, 00:00, 05:59; inactive at 06:00 and 21:59.
        val start = min(22)
        val end = min(6)
        assertTrue(NotificationWindow.isActive(min(22), start, end))
        assertTrue(NotificationWindow.isActive(min(23, 30), start, end))
        assertTrue(NotificationWindow.isActive(min(0), start, end))
        assertTrue(NotificationWindow.isActive(min(5, 59), start, end))
        assertFalse(NotificationWindow.isActive(min(6), start, end))
        assertFalse(NotificationWindow.isActive(min(21, 59), start, end))
        assertFalse(NotificationWindow.isActive(min(12), start, end))
    }

    @Test
    fun end_of_day_sentinel_includes_last_minute() {
        // start=0, end=1440 means "all day".
        assertTrue(NotificationWindow.isActive(min(0), 0, 1440))
        assertTrue(NotificationWindow.isActive(min(23, 59), 0, 1440))
    }

    @Test
    fun start_equals_end_treated_as_always_active() {
        assertTrue(NotificationWindow.isActive(min(3), min(9), min(9)))
        assertTrue(NotificationWindow.isActive(min(15), min(9), min(9)))
    }

    @Test
    fun forward_window_one_minute_wide() {
        // Exactly the minute the window opens is inside; one minute later is out.
        assertTrue(NotificationWindow.isActive(min(9), min(9), min(9, 1)))
        assertFalse(NotificationWindow.isActive(min(9, 1), min(9), min(9, 1)))
    }
}
