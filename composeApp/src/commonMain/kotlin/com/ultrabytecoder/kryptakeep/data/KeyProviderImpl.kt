package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.service.EncryptionService

class KeyProviderImpl(
    private val walletRepository: WalletRepository,
    private val encryptionService: EncryptionService
) : KeyProvider {
    override suspend fun getMasterSeed(walletId: Long): ByteArray {
        val encryptedSeed = walletRepository.getMasterSeed(walletId)
            ?: throw IllegalArgumentException("Wallet not found: $walletId")
        return encryptionService.decrypt(encryptedSeed)
    }
}
