package io.github.xsaveopt.cryptsync.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystore: KeystoreCrypto,
    private val argon2: Argon2Verifier,
) {
    private val encryptedFile: File get() = File(context.filesDir, "credential.bin")
    private val verifierFile: File get() = File(context.filesDir, "credential.argon")

    fun hasPassword(): Boolean = encryptedFile.exists() && verifierFile.exists()

    fun setPassword(password: String) {
        val encrypted = keystore.encrypt(password.toByteArray(Charsets.UTF_8))
        encryptedFile.writeBytes(encrypted)
        verifierFile.writeText(argon2.hash(password).encode())
    }

    fun verify(password: String): Boolean {
        if (!verifierFile.exists()) return false
        return argon2.verify(password, Argon2Hash.decode(verifierFile.readText()))
    }

    fun getPassword(): String? {
        if (!encryptedFile.exists()) return null
        val decrypted = keystore.decrypt(encryptedFile.readBytes())
        return String(decrypted, Charsets.UTF_8)
    }

    fun clear() {
        encryptedFile.delete()
        verifierFile.delete()
    }

    fun encodedVerifier(): String? =
        if (verifierFile.exists()) verifierFile.readText() else null

    fun base64Encrypted(): String? =
        if (encryptedFile.exists()) Base64.getEncoder().encodeToString(encryptedFile.readBytes()) else null
}
