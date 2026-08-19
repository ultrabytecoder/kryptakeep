package com.ultrabytecoder.kryptakeep.security

import com.ultrabytecoder.kryptakeep.data.appDataDir
import com.ultrabytecoder.kryptakeep.data.restrictFileToOwner
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Desktop device key.
 *
 * There is no OS-backed keystore abstraction on plain JVM without extra native
 * dependencies, so the key is a random AES-256 key stored in
 * `<user.home>/.kryptakeep/.device_key` with owner-only permissions
 * (0600 on POSIX). This binds the key hierarchy to the app data directory —
 * the same threat-model position as Android Keystore, minus the hardware
 * backing (no StrongBox/TEE on desktop without TPM integration).
 *
 * The key file is created lazily on first [encrypt].
 */
actual object HardwareKeyStore {

    private const val KEY_ALIAS = "kryptakeep_device_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private val baseDir = appDataDir()
    private val keyFile = File(baseDir, ".device_key")

    private val keyLock = Any()

    private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
        if (!keyFile.exists()) {
            baseDir.mkdirs()
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            keyFile.writeBytes(key.encoded)
            restrictFileToOwner(keyFile)
        }
        val encoded = keyFile.readBytes()
        javax.crypto.spec.SecretKeySpec(encoded, "AES").also {
            encoded.wipe()
        }
    }

    actual fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    actual fun decrypt(encrypted: ByteArray): ByteArray {
        if (!keyFile.exists()) {
            throw HardwareKeyInvalidatedException("Device hardware key missing")
        }
        require(encrypted.size > GCM_IV_LENGTH) { "Encrypted data too short" }
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw AesGcmAuthenticationException("Hardware GCM authentication failed")
        }
    }

    actual fun deleteKey() {
        keyFile.delete()
    }
}