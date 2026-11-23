package com.appname.notes.feature.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Step 4 — Hardware-Backed Android Keystore Encryption (AES-256-GCM) & Salted PBKDF2 PIN Verifier.
 * - Uses Android's native `AndroidKeyStore` (no external crypto libraries required).
 * - Uses PBKDF2WithHmacSHA256 (120,000 iterations + 16-byte random salt + constant-time compare)
 *   for fallback PIN verification on devices without enrolled biometrics.
 */
object SecureNoteKeystore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "papriva_note_lock_aes256_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256

    private val secureRandom = SecureRandom()

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    /**
     * Encrypts UTF-8 plaintext with AES-256-GCM and returns `base64(iv):base64(ciphertext)`.
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivB64:$ctB64"
    }

    /**
     * Decrypts a payload produced by [encrypt]. Returns `null` if tampered or invalid.
     */
    fun decrypt(encodedPayload: String): String? = runCatching {
        val parts = encodedPayload.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    /**
     * Hashes a user PIN with a fresh 16-byte random salt using PBKDF2-HMAC-SHA256.
     * Format: `v1:<iterations>:<hexSalt>:<hexHash>`
     */
    fun hashPin(pin: CharArray): String {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val derived = derivePbkdf2(pin, salt, PBKDF2_ITERATIONS)
        return "v1:$PBKDF2_ITERATIONS:${salt.toHex()}:${derived.toHex()}"
    }

    /**
     * Constant-time verification of a candidate PIN against a stored [hashPin] record.
     */
    fun verifyPin(candidatePin: CharArray, storedRecord: String): Boolean {
        val parts = storedRecord.split(":")
        if (parts.size != 4 || parts[0] != "v1") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations < 10_000) return false
        val salt = parts[2].fromHexOrNull() ?: return false
        val expectedHash = parts[3].fromHexOrNull() ?: return false
        val actualHash = derivePbkdf2(candidatePin, salt, iterations)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    private fun derivePbkdf2(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, PBKDF2_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHexOrNull(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        return runCatching {
            ByteArray(length / 2) { idx ->
                substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
