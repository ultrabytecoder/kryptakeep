package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.service.EncryptionService

class GetMnemonicUseCase(
    private val walletRepository: WalletRepository,
    private val encryptionService: EncryptionService
) {
    suspend operator fun invoke(walletId: Long): String? {
        val encryptedMnemonic = walletRepository.getEncryptedMnemonic(walletId) ?: return null
        val decryptedBytes = encryptionService.decrypt(encryptedMnemonic)
        return decryptedBytes.decodeToString()
    }
}
