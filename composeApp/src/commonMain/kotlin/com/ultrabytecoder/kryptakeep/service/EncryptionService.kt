package com.ultrabytecoder.kryptakeep.service

expect class EncryptionService(context: Any? = null) {
    fun encrypt(plainData: ByteArray): ByteArray
    fun decrypt(encryptedData: ByteArray): ByteArray
}
