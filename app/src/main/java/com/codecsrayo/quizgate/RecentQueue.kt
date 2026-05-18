package com.codecsrayo.quizgate

/**
 * FIFO queue of recently-shown ids with dedup on push and bounded size.
 *
 * Pure data manipulation — used by Prefs to avoid replaying the same
 * question or glossary term repeatedly. Encoded on disk as a single
 * pipe-delimited string; encode/decode kept here so the round-trip is
 * directly testable on the host JVM.
 */
object RecentQueue {

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split('|')
    }

    fun encode(ids: List<String>): String = ids.joinToString("|")

    /**
     * Returns a new list with [id] moved to the most-recent end. Any prior
     * occurrence is removed so the queue stays unique, and the front is
     * trimmed once the bound is exceeded. A non-positive [maxSize] yields the
     * current list unchanged.
     */
    fun push(current: List<String>, id: String, maxSize: Int): List<String> {
        if (maxSize <= 0) return current
        val next = ArrayList<String>(current.size + 1)
        for (existing in current) if (existing != id) next.add(existing)
        next.add(id)
        while (next.size > maxSize) next.removeAt(0)
        return next
    }
}
