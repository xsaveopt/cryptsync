package io.github.xsaveopt.cryptsync.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class Argon2Hash(val saltBase64: String, val hashBase64: String) {
    fun encode(): String = "$saltBase64:$hashBase64"

    companion object {
        fun decode(value: String): Argon2Hash {
            val parts = value.split(":")
            require(parts.size == 2) { "Malformed Argon2 hash" }
            return Argon2Hash(parts[0], parts[1])
        }
    }
}

@Singleton
class Argon2Verifier @Inject constructor() {

    fun hash(password: String): Argon2Hash {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt)
        val encoder = Base64.getEncoder()
        return Argon2Hash(encoder.encodeToString(salt), encoder.encodeToString(derived))
    }

    fun verify(password: String, stored: Argon2Hash): Boolean {
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(stored.saltBase64)
        val expected = decoder.decode(stored.hashBase64)
        val actual = derive(password, salt)
        return constantTimeEquals(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(ITERATIONS)
            .withMemoryAsKB(MEMORY_KB)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val output = ByteArray(HASH_LENGTH)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), output)
        return output
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private companion object {
        const val SALT_LENGTH = 16
        const val HASH_LENGTH = 32
        const val ITERATIONS = 3
        const val MEMORY_KB = 65536
        const val PARALLELISM = 2
    }
}
