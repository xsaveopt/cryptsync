package io.github.xsaveopt.cryptsync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon2HashTest {

    private fun rejects(value: String) {
        val error = runCatching { Argon2Hash.decode(value) }.exceptionOrNull()
        assertTrue("expected $value to be rejected but got $error", error is IllegalArgumentException)
    }

    @Test
    fun decodeSplitsSaltAndHash() {
        assertEquals(Argon2Hash("c2FsdA==", "aGFzaA=="), Argon2Hash.decode("c2FsdA==:aGFzaA=="))
    }

    @Test
    fun encodeJoinsWithColon() {
        assertEquals("c2FsdA==:aGFzaA==", Argon2Hash("c2FsdA==", "aGFzaA==").encode())
    }

    @Test
    fun decodeRejectsMissingSeparator() {
        rejects("c2FsdA==")
        rejects("")
    }

    @Test
    fun decodeRejectsExtraSeparators() {
        rejects("a:b:c")
    }

    @Test
    fun decodeRejectsEmptyParts() {
        rejects(":")
        rejects("c2FsdA==:")
        rejects(":aGFzaA==")
    }
}
