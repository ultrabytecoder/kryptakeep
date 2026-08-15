package com.ultrabytecoder.kryptakeep.security

import android.os.Build
import java.security.Key
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Wraps the password bytes WITHOUT copying them (SecretKeySpec clones the array
 * into its own storage, which can then never be wiped — NEW-6). The caller owns
 * the referenced array and wipes it once the derivation is complete.
 */
private class NonCopyingSecretKey(
    private val bytes: ByteArray
) : Key {
    override fun getAlgorithm(): String = "HmacSHA256"
    override fun getFormat(): String = "RAW"
    override fun getEncoded(): ByteArray = bytes
}

actual object Pbkdf2 {
    actual fun derive(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int
    ): ByteArray {
        // Prefer the audited, constant-time JCA implementation (API 26+).
        // It keeps intermediate blocks inside the provider (no application-heap
        // copies that would have to be wiped). Older API levels fall back to the
        // manual Mac loop below, wiping every intermediate array.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deriveWithJca(password, salt, iterations, derivedKeyLengthBytes)
        } else {
            deriveWithMac(password, salt, iterations, derivedKeyLengthBytes)
        }
    }

    private fun deriveWithJca(
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
            passwordChars.fill('\u0000')
        }
    }

    private fun deriveWithMac(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Non-copying key: SecretKeySpec would clone the password bytes into
        // unforgeable (unwipeable) storage. The provider may still retain an
        // internal copy for the lifetime of the Mac — a known residual limitation
        // of the JCA API on API < 26 devices.
        mac.init(NonCopyingSecretKey(password))

        val hashLength = 32
        val blocksNeeded = (derivedKeyLengthBytes + hashLength - 1) / hashLength
        val dk = ByteArray(derivedKeyLengthBytes)

        for (blockIndex in 1..blocksNeeded) {
            val saltWithCounter = salt + byteArrayOf(
                (blockIndex shr 24).toByte(),
                (blockIndex shr 16).toByte(),
                (blockIndex shr 8).toByte(),
                blockIndex.toByte()
            )
            val u = mac.doFinal(saltWithCounter)
            val result = u.copyOf()
            saltWithCounter.wipe()

            for (i in 2..iterations) {
                val nextU = mac.doFinal(u)
                for (j in result.indices) {
                    result[j] = (result[j].toInt() xor nextU[j].toInt()).toByte()
                }
                System.arraycopy(nextU, 0, u, 0, nextU.size)
                nextU.wipe()
            }

            val copyLength = minOf(hashLength, derivedKeyLengthBytes - (blockIndex - 1) * hashLength)
            System.arraycopy(result, 0, dk, (blockIndex - 1) * hashLength, copyLength)
            result.wipe()
            u.wipe()
        }
        return dk
    }
}