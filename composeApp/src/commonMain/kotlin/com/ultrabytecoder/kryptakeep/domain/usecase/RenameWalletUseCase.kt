package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository

class RenameWalletUseCase(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(id: Long, name: String) {
        walletRepository.renameWallet(id, name)
    }
}
