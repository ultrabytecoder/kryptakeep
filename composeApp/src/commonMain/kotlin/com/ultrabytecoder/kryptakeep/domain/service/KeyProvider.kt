package com.ultrabytecoder.kryptakeep.domain.service

interface KeyProvider {
    suspend fun getMasterSeed(walletId: Long): ByteArray
}
