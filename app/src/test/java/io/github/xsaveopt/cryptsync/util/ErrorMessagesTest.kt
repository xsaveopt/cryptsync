package io.github.xsaveopt.cryptsync.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun quotaErrorBecomesSpaceAdvice() {
        val msg = friendlyMessage(RuntimeException("storageQuotaExceeded: over limit"))
        assertTrue(msg.contains("space", ignoreCase = true))
    }

    @Test
    fun tokenErrorAsksToReconnect() {
        val msg = friendlyMessage(RuntimeException("invalid_grant"))
        assertTrue(msg.contains("Reconnect"))
    }

    @Test
    fun blankMessageFallsBack() {
        assertEquals("Something went wrong", friendlyMessage(RuntimeException()))
    }

    @Test
    fun otherMessagePassesThrough() {
        assertEquals("boom", friendlyMessage(RuntimeException("boom")))
    }
}
