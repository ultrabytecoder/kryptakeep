package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.service.EncryptionService

class GetMnemonicUseCase(
    private val walletRepository: WalletRepository,
    private val encryptionService: EncryptionService
) {
    suspend operator fun invoke(walletId: Long): CharArray? {
        val encryptedMnemonic = walletRepository.getEncryptedMnemonic(walletId) ?: return null
        var decryptedBytes: ByteArray? = null
        return try {
            decryptedBytes = encryptionService.decrypt(encryptedMnemonic)
            // Decode to CharArray without creating an intermediate String
            CharArray(decryptedBytes.size) { i -> decryptedBytes[i].toInt().toChar() }
        } finally {
            decryptedBytes?.wipe()
        }
    }
}