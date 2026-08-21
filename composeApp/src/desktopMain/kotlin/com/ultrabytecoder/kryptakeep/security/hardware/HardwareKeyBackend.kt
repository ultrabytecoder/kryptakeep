package com.ultrabytecoder.kryptakeep.security.hardware

/**
 * A device-key backend implementation selected by the [com.ultrabytecoder.kryptakeep.security.HardwareKeyStore]
 * dispatcher based on the host OS.
 *
 * Implementations must be safe for concurrent use (the dispatcher synchronizes
 * access) and must throw [com.ultrabytecoder.kryptakeep.security.HardwareKeyInvalidatedException]
 * when the device key is missing/invalidated and
 * [com.ultrabytecoder.kryptakeep.security.AesGcmAuthenticationException] when a
 * ciphertext fails authentication.
 */
interface HardwareKeyBackend {

    /** Stable identifier of this backend (for logging, UI badge, migration). */
    val id: String

    /** Encrypts [plaintext] with the device key (creating the key on first use). */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Decrypts [encrypted] with the device key. */
    fun decrypt(encrypted: ByteArray): ByteArray

    /** Deletes the device key. The key is re-created lazily on the next [encrypt]. */
    fun deleteKey()
}
