package com.codecsrayo.quizgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCodecTest {

    @Test
    fun decode_null_returns_empty() {
        assertTrue(StatsCodec.decode(null).isEmpty())
    }

    @Test
    fun decode_empty_returns_empty() {
        assertTrue(StatsCodec.decode("").isEmpty())
    }

    @Test
    fun decode_malformed_top_level_returns_empty() {
        assertTrue(StatsCodec.decode("not json").isEmpty())
    }

    @Test
    fun decode_skips_non_object_values() {
        // "x" is a primitive, not an object — codec silently skips it instead of throwing.
        val raw = """{"x":42,"q1":{"s":1,"c":2,"w":3}}"""
        val decoded = StatsCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(Prefs.QStat(1, 2, 3), decoded["q1"])
    }

    @Test
    fun decode_uses_zero_defaults_for_missing_fields() {
        val raw = """{"q1":{},"q2":{"s":5}}"""
        val decoded = StatsCodec.decode(raw)
        assertEquals(Prefs.QStat(0, 0, 0), decoded["q1"])
        assertEquals(Prefs.QStat(5, 0, 0), decoded["q2"])
    }

    @Test
    fun encode_decode_round_trip_preserves_values() {
        val original = mapOf(
            "q1" to Prefs.QStat(shown = 10, correct = 7, wrong = 3),
            "q2" to Prefs.QStat(shown = 0, correct = 0, wrong = 0),
            "q3" to Prefs.QStat(shown = 1, correct = 0, wrong = 1),
        )
        val decoded = StatsCodec.decode(StatsCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun encode_empty_produces_empty_json_object() {
        assertEquals("{}", StatsCodec.encode(emptyMap()))
    }
}
