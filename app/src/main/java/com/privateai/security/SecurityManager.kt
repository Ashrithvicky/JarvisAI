package com.privateai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Security layer for encrypting sensitive data using Android Keystore.
 *
 * SECURITY MODEL:
 * - Uses Android Keystore for hardware-backed key storage
 * - AES-256-GCM for symmetric encryption
 * - EncryptedSharedPreferences for small data (tokens, config)
 * - File-based encryption for large data (memories, conversations)
 *
 * THREAT MODEL (to be completed in SECURITY.md):
 * - Protects against offline data extraction from device storage
 * - Keys are bound to the device's secure hardware
 * - Keys cannot be extracted from the Keystore
 *
 * PLACEHOLDER: This is the initial security architecture.
 * Phase 2 will add:
 * - Key rotation
 * - Biometric-gated key access
 * - Secure enclave integration
 * - Memory encryption at rest
 */
open class SecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityManager"
        private const val KEYSTORE_ALIAS = "private_ai_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context, KEYSTORE_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "private_ai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Initialize the security manager and create encryption keys.
     */
    fun initialize(): Result<Unit> = runCatching {
        // Ensure the master key exists in Android Keystore
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            generateKey()
        }
        Log.d(TAG, "Security manager initialized")
    }

    /**
     * Generate a new encryption key in Android Keystore.
     */
    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Phase 2: enable with biometric
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt a string using the master key.
     * Returns Base64-encoded ciphertext with IV prepended.
     */
    fun encrypt(plaintext: String): String = runCatching {
        val key = getKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext: IV (12 bytes) + ciphertext
        val combined = iv + ciphertext
        android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }.getOrThrow()

    /**
     * Decrypt a Base64-encoded ciphertext string.
     */
    fun decrypt(ciphertext: String): String = runCatching {
        val key = getKey()
        val combined = android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)

        // Extract IV (first 12 bytes) and ciphertext
        val iv = combined.sliceArray(0..11)
        val encryptedData = combined.sliceArray(12 until combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decrypted = cipher.doFinal(encryptedData)
        String(decrypted, Charsets.UTF_8)
    }.getOrThrow()

    /**
     * Encrypt a byte array using the master key.
     * Returns bytes with 12-byte IV prepended to ciphertext.
     */
    fun encryptBytes(data: ByteArray): ByteArray = runCatching {
        val key = getKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        iv + ciphertext
    }.getOrThrow()

    /**
     * Decrypt a byte array with prepended 12-byte IV using the master key.
     */
    fun decryptBytes(encryptedData: ByteArray): ByteArray = runCatching {
        val key = getKey()
        val iv = encryptedData.sliceArray(0..11)
        val ciphertext = encryptedData.sliceArray(12 until encryptedData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        cipher.doFinal(ciphertext)
    }.getOrThrow()

    /**
     * Encrypt raw bytes and write directly to a destination file.
     * Eliminates the need for unencrypted temporary files on disk.
     */
    open fun encryptToFile(data: ByteArray, destFile: File): Result<Unit> = runCatching {
        val encrypted = encryptBytes(data)
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(encrypted)
        Log.d(TAG, "File encrypted directly: ${destFile.absolutePath}")
    }

    /**
     * Encrypt a file and write it to a new location.
     */
    open fun encryptFile(sourceFile: File, destFile: File): Result<Unit> = runCatching {
        val key = getKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(sourceFile.readBytes())

        destFile.parentFile?.mkdirs()
        destFile.outputStream().use { fos ->
            fos.write(iv) // 12 bytes IV
            fos.write(ciphertext)
        }
        Log.d(TAG, "File encrypted: ${destFile.absolutePath}")
    }

    /**
     * Decrypt a file and return the decrypted bytes.
     */
    open fun decryptFile(encryptedFile: File): Result<ByteArray> = runCatching {
        val data = encryptedFile.readBytes()
        decryptBytes(data)
    }

    /**
     * Store a secure value in EncryptedSharedPreferences.
     */
    fun putSecureString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    /**
     * Retrieve a secure value from EncryptedSharedPreferences.
     */
    fun getSecureString(key: String, default: String = ""): String {
        return encryptedPrefs.getString(key, default) ?: default
    }

    /**
     * Remove a secure value.
     */
    fun removeSecureString(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    /**
     * Get the encryption key from Android Keystore.
     */
    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    /**
     * Delete all security-related data.
     */
    fun deleteAll() {
        encryptedPrefs.edit().clear().apply()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(KEYSTORE_ALIAS)
        Log.d(TAG, "All security data deleted")
    }
}
