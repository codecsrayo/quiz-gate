package com.example.ask

import org.json.JSONObject

/**
 * Pure JSON codec for the per-question shown/correct/wrong counters that
 * Prefs persists. Lives apart from Prefs so the round-trip logic can be
 * unit-tested on the host JVM (no Android classes required).
 *
 * The on-disk shape is a flat object keyed by question id, where each value
 * is `{ "s": shown, "c": correct, "w": wrong }`. Unknown keys are tolerated;
 * malformed top-level JSON yields an empty map rather than throwing.
 */
object StatsCodec {

    fun decode(raw: String?): Map<String, Prefs.QStat> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val out = HashMap<String, Prefs.QStat>(obj.length())
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = obj.optJSONObject(k) ?: continue
                out[k] = Prefs.QStat(v.optInt("s", 0), v.optInt("c", 0), v.optInt("w", 0))
            }
            out
        }.getOrDefault(emptyMap())
    }

    fun encode(stats: Map<String, Prefs.QStat>): String {
        val obj = JSONObject()
        for ((k, v) in stats) {
            obj.put(k, JSONObject().put("s", v.shown).put("c", v.correct).put("w", v.wrong))
        }
        return obj.toString()
    }
}
