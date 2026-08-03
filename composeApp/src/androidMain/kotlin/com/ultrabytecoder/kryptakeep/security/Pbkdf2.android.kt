package com.ultrabytecoder.kryptakeep.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object Pbkdf2 {
    actual fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.encodeToByteArray(), "HmacSHA256"))

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

            for (i in 2..iterations) {
                val nextU = mac.doFinal(u)
                for (j in result.indices) {
                    result[j] = (result[j].toInt() xor nextU[j].toInt()).toByte()
                }
                System.arraycopy(nextU, 0, u, 0, nextU.size)
            }

            val copyLength = minOf(hashLength, derivedKeyLengthBytes - (blockIndex - 1) * hashLength)
            System.arraycopy(result, 0, dk, (blockIndex - 1) * hashLength, copyLength)
        }
        return dk
    }
}