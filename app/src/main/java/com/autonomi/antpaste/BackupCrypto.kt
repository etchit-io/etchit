package com.autonomi.antpaste

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts/decrypts private etch backups using AES-256-GCM with
 * PBKDF2-derived keys. The format is designed to be decryptable
 * with standard tools (OpenSSL, Python, etc.).
 *
 * Binary format:
 *   ETCHIT_BACKUP_v1\n   (17 bytes magic header)
 *   salt                  (16 bytes)
 *   iv                    (12 bytes)
 *   ciphertext+tag        (remaining bytes, GCM tag appended)
 *
 * Key derivation: PBKDF2-HMAC-SHA256, 600_000 iterations, 256-bit key.
 */
object BackupCrypto {

    private const val MAGIC = "ETCHIT_BACKUP_v1\n"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_LEN = 256
    private const val ITERATIONS = 600_000
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        return magic + salt + iv + ciphertext
    }

    fun decrypt(data: ByteArray, password: String): ByteArray? {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        if (data.size < magic.size + SALT_LEN + IV_LEN + 1) return null
        if (!data.copyOfRange(0, magic.size).contentEquals(magic)) return null

        var offset = magic.size
        val salt = data.copyOfRange(offset, offset + SALT_LEN); offset += SALT_LEN
        val iv = data.copyOfRange(offset, offset + IV_LEN); offset += IV_LEN
        val ciphertext = data.copyOfRange(offset, data.size)

        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null // Wrong password or corrupted data
        }
    }

    /** Check if a byte array looks like an etchit backup (magic header match). */
    fun isBackup(data: ByteArray): Boolean {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        if (data.size < magic.size) return false
        return data.copyOfRange(0, magic.size).contentEquals(magic)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
