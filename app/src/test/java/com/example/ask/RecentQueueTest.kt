package com.example.ask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentQueueTest {

    @Test
    fun decode_null_returns_empty() {
        assertTrue(RecentQueue.decode(null).isEmpty())
    }

    @Test
    fun decode_empty_string_returns_empty() {
        assertTrue(RecentQueue.decode("").isEmpty())
    }

    @Test
    fun decode_single_id_returns_singleton() {
        assertEquals(listOf("a"), RecentQueue.decode("a"))
    }

    @Test
    fun decode_pipe_separated_preserves_order() {
        assertEquals(listOf("a", "b", "c"), RecentQueue.decode("a|b|c"))
    }

    @Test
    fun encode_round_trip() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, RecentQueue.decode(RecentQueue.encode(ids)))
    }

    @Test
    fun push_appends_new_id_to_end() {
        assertEquals(listOf("a", "b", "c"), RecentQueue.push(listOf("a", "b"), "c", 10))
    }

    @Test
    fun push_existing_id_moves_it_to_end_no_dupes() {
        // "a" was at the front; pushing again moves it to the back and leaves no duplicate.
        assertEquals(listOf("b", "c", "a"), RecentQueue.push(listOf("a", "b", "c"), "a", 10))
    }

    @Test
    fun push_trims_oldest_when_over_capacity() {
        assertEquals(listOf("b", "c", "d"), RecentQueue.push(listOf("a", "b", "c"), "d", 3))
    }

    @Test
    fun push_with_zero_capacity_is_noop() {
        val current = listOf("a", "b")
        assertEquals(current, RecentQueue.push(current, "c", 0))
    }

    @Test
    fun push_with_negative_capacity_is_noop() {
        val current = listOf("a", "b")
        assertEquals(current, RecentQueue.push(current, "c", -5))
    }

    @Test
    fun push_into_empty_returns_singleton() {
        assertEquals(listOf("a"), RecentQueue.push(emptyList(), "a", 10))
    }

    @Test
    fun push_does_not_grow_when_re_pushing_existing_at_capacity() {
        // Already full at capacity 3; pushing an existing element should still
        // fit within the bound (dedup happens before trim).
        assertEquals(listOf("a", "c", "b"), RecentQueue.push(listOf("a", "b", "c"), "b", 3))
    }
}
