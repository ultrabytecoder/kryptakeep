package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CreateAccountUseCase(
    private val accountRepository: AccountRepository
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(walletId: Long, displayName: String, type: AccountType, symbol: String, params: String? = null) {
        val maxIndex = accountRepository.getMaxDerivationIndexByWalletAndAccountType(walletId, type.toDbCode())
        val derivationIndex = (maxIndex ?: -1) + 1
        val accountNameIndex = derivationIndex + 1
        val account = AccountInfo(
            id = Uuid.random().toString(),
            walletId = walletId,
            name = "$displayName account $accountNameIndex",
            amount = "0",
            type = type,
            symbol = symbol,
            address = null,
            derivationIndex = derivationIndex,
            params = params
        )
        accountRepository.insertAccount(account)
    }
}
