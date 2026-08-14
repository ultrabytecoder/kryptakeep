package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.service.EncryptionService

class KeyProviderImpl(
    private val walletRepository: WalletRepository,
    private val encryptionService: EncryptionService
) : KeyProvider {
    override suspend fun <T> withMasterSeed(walletId: Long, block: suspend (ByteArray) -> T): T {
        val encryptedSeed = walletRepository.getMasterSeed(walletId)
            ?: throw IllegalArgumentException("Wallet not found: $walletId")

        val seed = encryptionService.decrypt(encryptedSeed)
        return try {
            block(seed)
        } finally {
            seed.wipe()
        }
    }
}