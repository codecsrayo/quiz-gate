package com.codecsrayo.quizgate

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the seam introduced by the PrefsBackend interface: tests install
 * an in-memory fake, drive the Prefs facade with `null` for the Context
 * argument (the facade never calls into a non-null backend after install),
 * and observe state changes through the same fake. Proves that recordShown /
 * recordAnswer / pushRecentQuestionId / consumePendingUnlock are reachable
 * from unit tests without Robolectric.
 */
class PrefsFakeBackendTest {

    private val fake = FakePrefsBackend()
    private val ctx: android.content.Context? = null

    @Before
    fun installFake() {
        Prefs.installForTest(fake)
    }

    @After
    fun resetBackend() {
        Prefs.reset()
    }

    @Test
    fun recordAnswer_updates_stats_through_facade() {
        Prefs.recordAnswer(ctx, "q1", correct = true)
        Prefs.recordAnswer(ctx, "q1", correct = false)
        Prefs.recordAnswer(ctx, "q1", correct = false)
        Prefs.recordShown(ctx, "q1")

        val s = Prefs.getStats(ctx)["q1"]
        assertEquals(Prefs.QStat(shown = 1, correct = 1, wrong = 2), s)
    }

    @Test
    fun recordShown_independent_per_question() {
        Prefs.recordShown(ctx, "a")
        Prefs.recordShown(ctx, "a")
        Prefs.recordShown(ctx, "b")
        val stats = Prefs.getStats(ctx)
        assertEquals(2, stats["a"]?.shown)
        assertEquals(1, stats["b"]?.shown)
    }

    @Test
    fun pushRecentQuestionId_dedupes_and_trims() {
        Prefs.pushRecentQuestionId(ctx, "a", maxSize = 3)
        Prefs.pushRecentQuestionId(ctx, "b", maxSize = 3)
        Prefs.pushRecentQuestionId(ctx, "c", maxSize = 3)
        Prefs.pushRecentQuestionId(ctx, "d", maxSize = 3)
        Prefs.pushRecentQuestionId(ctx, "b", maxSize = 3) // dedup-then-trim
        assertEquals(listOf("c", "d", "b"), Prefs.getRecentQuestionIds(ctx))
    }

    @Test
    fun pendingUnlock_consume_succeeds_within_ttl_and_clears() {
        fake.nowProvider = { 1_000L }
        Prefs.setPendingUnlock(ctx, "com.whatsapp")
        fake.nowProvider = { 30_000L } // within 60s window
        assertTrue(Prefs.consumePendingUnlock(ctx, "com.whatsapp"))
        // Single-use: second attempt must fail.
        assertFalse(Prefs.consumePendingUnlock(ctx, "com.whatsapp"))
    }

    @Test
    fun pendingUnlock_consume_for_wrong_pkg_does_not_clear() {
        fake.nowProvider = { 0L }
        Prefs.setPendingUnlock(ctx, "com.whatsapp")
        assertFalse(Prefs.consumePendingUnlock(ctx, "com.instagram.android"))
        // Real pkg should still consume successfully.
        assertTrue(Prefs.consumePendingUnlock(ctx, "com.whatsapp"))
    }

    @Test
    fun pendingUnlock_expires_after_ttl() {
        fake.nowProvider = { 1_000L }
        Prefs.setPendingUnlock(ctx, "com.whatsapp")
        fake.nowProvider = { 90_000L } // > 60s TTL
        assertFalse(Prefs.consumePendingUnlock(ctx, "com.whatsapp"))
    }

    @Test
    fun sessionWindow_true_within_5_minutes_of_lastSeen() {
        fake.nowProvider = { 0L }
        Prefs.touchLastSeen(ctx, "com.whatsapp")
        fake.nowProvider = { 4 * 60_000L }
        assertTrue(Prefs.isWithinSessionWindow(ctx, "com.whatsapp"))
        fake.nowProvider = { 6 * 60_000L }
        assertFalse(Prefs.isWithinSessionWindow(ctx, "com.whatsapp"))
    }

    @Test
    fun setLastBlockedPackage_null_clears() {
        Prefs.setLastBlockedPackage(ctx, "com.whatsapp")
        assertEquals("com.whatsapp", Prefs.getLastBlockedPackage(ctx))
        Prefs.setLastBlockedPackage(ctx, null)
        assertNull(Prefs.getLastBlockedPackage(ctx))
    }

    @Test
    fun enabledDomains_empty_set_is_treated_as_null() {
        Prefs.setEnabledDomains(ctx, emptySet())
        assertNull(Prefs.getEnabledDomainsOrNull(ctx))
        Prefs.setEnabledDomains(ctx, setOf("d1"))
        assertEquals(setOf("d1"), Prefs.getEnabledDomainsOrNull(ctx))
    }

    @Test
    fun glossary_push_interval_floor_is_15_minutes() {
        Prefs.setGlossaryPushIntervalMin(ctx, 5)
        assertEquals(15, Prefs.getGlossaryPushIntervalMin(ctx))
        Prefs.setGlossaryPushIntervalMin(ctx, 120)
        assertEquals(120, Prefs.getGlossaryPushIntervalMin(ctx))
    }
}
