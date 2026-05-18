package com.codecsrayo.quizgate

/**
 * Pure "is the current time inside the active window" check used by the
 * glossary push worker to skip notifications outside the user's schedule.
 *
 * Minute-of-day granularity (0..1439) so the same code can back any future
 * minute-precision UI without changing on-disk shape.
 */
object NotificationWindow {

    /**
     * True if [nowMin] falls in `[startMin, endMin)`.
     *
     * Wraparound is supported: when `startMin > endMin` the window crosses
     * midnight (e.g. 22:00–06:00). When `startMin == endMin` the window is
     * treated as always-active (degenerate case used by the "no schedule"
     * default).
     *
     * `endMin == 1440` means "until end of day" and is honoured by the
     * forward branch — `nowMin < 1440` is always true for any clock value.
     */
    fun isActive(nowMin: Int, startMin: Int, endMin: Int): Boolean {
        if (startMin == endMin) return true
        return if (startMin < endMin) {
            nowMin in startMin until endMin
        } else {
            nowMin >= startMin || nowMin < endMin
        }
    }
}
