package com.ultrabytecoder.kryptakeep.security

/**
 * Wraps stored secrets (recovery phrase and master seed) — defense in depth
 * on top of the SQLCipher database.
 *
 * The backend is platform-specific ([secretCipherEncrypt] / [secretCipherDecrypt]):
 * Mobile uses the OS hardware key ([HardwareKeyStore], Android Keystore / iOS
 * Secure Enclave); Desktop uses a dedicated random AES-256 key stored in
 * settings, so even a DEK exfiltration does not reveal the secret BLOBs.
 *
 * Format: `"KRYPT" || 0x01 || backend.encrypt(plaintext)`. The magic prefix
 * distinguishes new ciphertext from legacy plaintext BLOBs written by
 * pre-hardware-encryption dev builds.
 */
object SecretCipher {

    private val MAGIC: ByteArray = byteArrayOf(0x4B, 0x52, 0x59, 0x50, 0x54, 0x01) // "KRYPT" v1

    fun encrypt(plaintext: ByteArray): ByteArray {
        val blob = secretCipherEncrypt(plaintext)
        return try {
            MAGIC + blob
        } finally {
            blob.wipe()
        }
    }

    fun decrypt(stored: ByteArray): Result {
        if (!stored.startsWith(MAGIC)) {
            return Result.Legacy(stored)
        }
        val blob = stored.copyOfRange(MAGIC.size, stored.size)
        return try {
            Result.Encrypted(secretCipherDecrypt(blob))
        } finally {
            blob.wipe()
        }
    }

    sealed interface Result {
        class Encrypted(val plaintext: ByteArray) : Result
        class Legacy(val plaintext: ByteArray) : Result
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}

internal expect fun secretCipherEncrypt(plaintext: ByteArray): ByteArray
internal expect fun secretCipherDecrypt(encrypted: ByteArray): ByteArray
internal expect fun secretCipherDeleteKey()
