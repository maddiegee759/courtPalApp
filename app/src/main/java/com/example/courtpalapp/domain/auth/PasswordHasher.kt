package com.example.courtpalapp.domain.auth

import android.os.Build
import androidx.annotation.RequiresApi
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 20

    private const val ITERATIONS = 150_000
    private const val SALT_BYTES = 16
    private const val KEY_LENGTH_BITS = 256

    private const val VERSION = "pbkdf2-sha256"

    fun isValidPassword(password: String): Boolean {
        return password.length in
                MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createVerifier(password: String): String {
        require(isValidPassword(password)) {
            "Password must be 8–25 characters"
        }

        val salt = ByteArray(SALT_BYTES)

        SecureRandom().nextBytes(salt)

        val hash = deriveKey(
            password = password,
            salt = salt,
            iterations = ITERATIONS
        )

        val encodedSalt = Base64.getEncoder()
            .encodeToString(salt)

        val encodedHash = Base64.getEncoder()
            .encodeToString(hash)

        return "$VERSION:$ITERATIONS:$encodedSalt:$encodedHash"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun verify(
        password: String,
        storedVerifier: String
    ): Boolean {
        return try {
            val parts = storedVerifier.split(":")

            if (parts.size != 4 || parts[0] != VERSION) {
                return false
            }

            val iterations = parts[1].toIntOrNull()
                ?: return false

            if (iterations !in 100_000..1_000_000) {
                return false
            }

            val salt = Base64.getDecoder()
                .decode(parts[2])

            val expectedHash = Base64.getDecoder()
                .decode(parts[3])

            if (
                salt.size != SALT_BYTES ||
                expectedHash.size != KEY_LENGTH_BITS / 8
            ) {
                return false
            }

            val actualHash = deriveKey(
                password = password,
                salt = salt,
                iterations = iterations
            )

            MessageDigest.isEqual(
                expectedHash,
                actualHash
            )

        } catch (_: Exception) {
            false
        }
    }

    fun needsRehash(storedVerifier: String): Boolean {
        val parts = storedVerifier.split(":")

        if (parts.size != 4 || parts[0] != VERSION) {
            return false
        }

        val storedIterations = parts[1].toIntOrNull()
            ?: return false

        return storedIterations != ITERATIONS
    }

    private fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val characters = password.toCharArray()

        val specification = PBEKeySpec(
            characters,
            salt,
            iterations,
            KEY_LENGTH_BITS
        )

        return try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
        } finally {
            specification.clearPassword()
            characters.fill('\u0000')
        }
    }
}