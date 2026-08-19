package com.ultrabytecoder.kryptakeep.security

import java.security.Key
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * JVM PBKDF2-HMAC-SHA256 via the audited, constant-time JCA implementation
 * (the provider keeps intermediate blocks outside the application heap).
 */
actual object Pbkdf2 {

    actual fun derive(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int
    ): ByteArray {
        val passwordChars = CharArray(password.size) { password[it].toInt().toChar() }
        val spec = PBEKeySpec(passwordChars, salt, iterations, derivedKeyLengthBytes * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            passwordChars.wipe()
        }
    }
}