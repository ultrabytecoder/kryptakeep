package com.ultrabytecoder.kryptakeep.domain.service

sealed class BiometricAuthResult {
    class Success(val token: ByteArray) : BiometricAuthResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            other as Success
            return token.contentEquals(other.token)
        }

        override fun hashCode(): Int = token.contentHashCode()

        override fun toString(): String = "Success(<${token.size} bytes>)"
    }

    data object Cancelled : BiometricAuthResult()
    data object KeyInvalidated : BiometricAuthResult()
}

expect class BiometricService(context: Any? = null) {
    val isAvailable: Boolean

    suspend fun promptAndEncrypt(data: ByteArray): Boolean

    suspend fun promptAndDecrypt(): BiometricAuthResult

    fun clear()
}