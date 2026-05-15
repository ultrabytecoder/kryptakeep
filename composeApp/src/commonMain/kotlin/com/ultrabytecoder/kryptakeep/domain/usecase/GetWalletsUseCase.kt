package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow

class GetWalletsUseCase(
    private val walletRepository: WalletRepository
) {
    operator fun invoke(): Flow<List<WalletInfo>> =
        walletRepository.getWalletsFlow()
}
