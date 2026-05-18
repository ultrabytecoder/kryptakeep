package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateAccountUseCase
import com.ultrabytecoder.kryptakeep.providers.DerivationPathResolver

class CreateAccountViewModel(
    private val walletId: Long,
    private val createAccount: CreateAccountUseCase,
    private val accountRepository: AccountRepository,
    private val networkConfig: NetworkConfig
) : ViewModel() {

    suspend fun getDefaultDerivationPath(type: AccountType): String {
        val maxIndex = accountRepository.getMaxAccountIndexByWalletAndAccountType(walletId, type.toDbCode())
        val nextIndex = (maxIndex ?: -1) + 1
        return DerivationPathResolver.defaultPath(type, nextIndex, networkConfig)
    }

    suspend fun createAccount(
        displayName: String,
        type: AccountType,
        symbol: String,
        params: String? = null,
        derivationPath: String? = null
    ) = createAccount(walletId, displayName, type, symbol, params, derivationPath)
}
