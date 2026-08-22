package com.ultrabytecoder.kryptakeep.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-256-GCM key with `setUserAuthenticationRequired(false)` —
 * no biometric prompt; the key is usable whenever the device is unlocked.
 *
 * StrongBox is requested where available (API 28+), with a transparent fallback
 * to the TEE: any failure to create a StrongBox-backed key (unsupported device,
 * OEM quirks) retries with a plain spec. The key never leaves the Keystore.
 */
actual object HardwareKeyStore {

    private const val KEY_ALIAS = "kryptakeep_device_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val keyLock = Any()

    private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            generateWithFallback(keyGenerator, tryStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        }
        (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun generateWithFallback(keyGenerator: KeyGenerator, tryStrongBox: Boolean) {
        try {
            keyGenerator.init(buildSpec(strongBox = tryStrongBox))
            keyGenerator.generateKey()
        } catch (e: Exception) {
            if (!tryStrongBox) throw e
            val isStrongBoxFailure =
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && e is StrongBoxUnavailableException) ||
                    e is ProviderException
            if (!isStrongBoxFailure) throw e
            try {
                keyStore.deleteEntry(KEY_ALIAS)
            } catch (_: Exception) {
            }
            keyGenerator.init(buildSpec(strongBox = false))
            keyGenerator.generateKey()
        }
    }

    private fun buildSpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    actual fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    actual fun decrypt(encrypted: ByteArray, aad: ByteArray): ByteArray {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            throw HardwareKeyInvalidatedException("Device hardware key missing")
        }
        require(encrypted.size > GCM_IV_LENGTH) { "Encrypted data too short" }
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw AesGcmAuthenticationException("Hardware GCM authentication failed")
        }
    }

    actual fun deleteKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // Key already gone or Keystore unavailable — nothing to clean up.
        }
    }

    actual fun purgeCache() {
        // The Android Keystore key never leaves the Keystore — nothing cached to purge.
    }

    actual fun configureInstallId(id: ByteArray) {
        // The Keystore key is already device-bound — no install-id binding needed.
    }
}