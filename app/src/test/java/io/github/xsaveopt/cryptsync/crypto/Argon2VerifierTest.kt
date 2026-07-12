package io.github.xsaveopt.cryptsync.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon2VerifierTest {

    private val verifier = Argon2Verifier()

    @Test
    fun verifyAcceptsCorrectPassword() {
        val hash = verifier.hash("correct horse battery staple")
        assertTrue(verifier.verify("correct horse battery staple", hash))
    }

    @Test
    fun verifyRejectsWrongPassword() {
        val hash = verifier.hash("correct horse battery staple")
        assertFalse(verifier.verify("Tr0ub4dor&3", hash))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val hash = verifier.hash("a password")
        val decoded = Argon2Hash.decode(hash.encode())
        assertTrue(verifier.verify("a password", decoded))
    }

    @Test
    fun saltsDifferPerHash() {
        val first = verifier.hash("same password")
        val second = verifier.hash("same password")
        assertNotEquals(first.saltBase64, second.saltBase64)
    }
}
