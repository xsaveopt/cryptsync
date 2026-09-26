package io.github.xsaveopt.cryptsync.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun noSpaceLeftBecomesSpaceAdvice() {
        val msg = friendlyMessage(RuntimeException("write failed: no space left on device"))
        assertTrue(msg.contains("space", ignoreCase = true))
    }

    @Test
    fun unauthorizedAsksToReconnect() {
        val msg = friendlyMessage(RuntimeException("HTTP 401 Unauthorized"))
        assertTrue(msg.contains("Reconnect"))
    }

    @Test
    fun jsonParseErrorMentioningTokenPassesThrough() {
        val text = "Unexpected token < in JSON at position 0"
        assertEquals(text, friendlyMessage(RuntimeException(text)))
    }

    @Test
    fun insufficientPermissionsIsNotSpaceAdvice() {
        val text = "googleapi: Error 403: Insufficient Permission: Request had insufficient authentication scopes"
        assertFalse(friendlyMessage(RuntimeException(text)).contains("space", ignoreCase = true))
    }

    @Test
    fun rateLimitQuotaIsNotSpaceAdvice() {
        val text = "googleapi: Error 403: Quota exceeded for quota metric 'Queries' and limit 'Queries per minute', rateLimitExceeded"
        assertFalse(friendlyMessage(RuntimeException(text)).contains("space", ignoreCase = true))
    }

    @Test
    fun wrongResticPasswordPassesThrough() {
        val text = "Fatal: wrong password or no key found"
        assertEquals(text, friendlyMessage(RuntimeException(text)))
    }
}
