package com.ultrabytecoder.kryptakeep.security

/**
 * Platform-native PBKDF2-HMAC-SHA256 implementation.
 * - Android: javax.crypto.Mac (BoringSSL backed)
 * - iOS: CommonCrypto.CCKeyDerivationPBKDF
 */
expect object Pbkdf2 {
    fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int = 32
    ): ByteArray
}