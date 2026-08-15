package com.ultrabytecoder.kryptakeep.security

/**
 * Hardware-key wrapping for stored secrets (recovery phrase and master seed) —
 * defense in depth on top of the SQLCipher database: the secret BLOB is encrypted
 * with the always-on device key ([HardwareKeyStore], Android Keystore / iOS Secure
 * Enclave) before it is written, so even a decrypted database (e.g. DEK
 * exfiltration) does not reveal the secret, and the ciphertext is useless on
 * another device.
 *
 * Format: `"KRYPT" || 0x01 || HardwareKeyStore.encrypt(plaintext)`. The magic
 * prefix distinguishes new ciphertext from legacy plaintext BLOBs written by
 * pre-hardware-encryption installs. Detection must be unambiguous for both kinds
 * of legacy data:
 *  - mnemonics are printable ASCII (words and spaces) — cannot contain the 0x01
 *    control byte;
 *  - legacy seeds are 64 random bytes — matching the 5-byte magic + version has
 *    probability 2^-48, so a false "encrypted" detection is practically impossible.
 */
object SecretCipher {

    private val MAGIC: ByteArray = byteArrayOf(0x4B, 0x52, 0x59, 0x50, 0x54, 0x01) // "KRYPT" v1

    /**
     * Wraps [plaintext] with the device hardware key. The caller owns both the
     * input (wiped by the caller) and the returned ciphertext.
     *
     * @throws Exception when the hardware key cannot be created/used.
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val blob = HardwareKeyStore.encrypt(plaintext)
        return try {
            MAGIC + blob
        } finally {
            blob.wipe()
        }
    }

    /**
     * Decrypts a stored secret BLOB:
     * - [Result.Encrypted]: magic present — decrypted with the device hardware key.
     * - [Result.Legacy]: no magic — pre-hardware-encryption plaintext, returned as-is
     *   (the caller may re-encrypt it in place).
     *
     * @throws HardwareKeyInvalidatedException when the device key is missing/invalidated
     * and the stored blob is hardware-encrypted — the secret is unrecoverable.
     * @throws AesGcmAuthenticationException when the stored blob fails authentication
     * (tampered/corrupt) — never fall back to treating it as plaintext.
     */
    fun decrypt(stored: ByteArray): Result {
        if (!stored.startsWith(MAGIC)) {
            return Result.Legacy(stored)
        }
        val blob = stored.copyOfRange(MAGIC.size, stored.size)
        return try {
            Result.Encrypted(HardwareKeyStore.decrypt(blob))
        } finally {
            blob.wipe()
        }
    }

    sealed interface Result {
        /** Decrypted with the device hardware key; [plaintext] is a fresh array. */
        class Encrypted(val plaintext: ByteArray) : Result

        /** Legacy plaintext blob ([plaintext] is the stored array itself, not a copy). */
        class Legacy(val plaintext: ByteArray) : Result
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}