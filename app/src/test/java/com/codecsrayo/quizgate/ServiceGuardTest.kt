package com.codecsrayo.quizgate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceGuardTest {

    private val threshold = ServiceGuard.STALENESS_THRESHOLD_MS
    private val now = 1_000_000_000L

    @Test fun toggleOff_isDown() {
        assertTrue(ServiceGuard.isAccessibilityDown(toggleEnabled = false, heartbeatMs = now, nowMs = now))
    }

    @Test fun toggleOff_withNoHeartbeat_isDown() {
        assertTrue(ServiceGuard.isAccessibilityDown(toggleEnabled = false, heartbeatMs = 0L, nowMs = now))
    }

    @Test fun freshHeartbeat_isUp() {
        assertFalse(ServiceGuard.isAccessibilityDown(toggleEnabled = true, heartbeatMs = now - 1, nowMs = now))
    }

    @Test fun heartbeatJustWithinThreshold_isUp() {
        assertFalse(
            ServiceGuard.isAccessibilityDown(
                toggleEnabled = true, heartbeatMs = now - threshold + 1, nowMs = now,
            )
        )
    }

    @Test fun heartbeatExactlyAtThreshold_isDown() {
        assertTrue(
            ServiceGuard.isAccessibilityDown(
                toggleEnabled = true, heartbeatMs = now - threshold, nowMs = now,
            )
        )
    }

    @Test fun longStaleHeartbeat_isDown() {
        assertTrue(
            ServiceGuard.isAccessibilityDown(
                toggleEnabled = true, heartbeatMs = now - 6 * 60 * 60_000L, nowMs = now,
            )
        )
    }

    @Test fun noHeartbeatYet_withToggleOn_isNotDown() {
        // Fresh install / pre-seed window — don't false-alarm before the
        // service has had a chance to write its first heartbeat.
        assertFalse(ServiceGuard.isAccessibilityDown(toggleEnabled = true, heartbeatMs = 0L, nowMs = now))
    }
}
