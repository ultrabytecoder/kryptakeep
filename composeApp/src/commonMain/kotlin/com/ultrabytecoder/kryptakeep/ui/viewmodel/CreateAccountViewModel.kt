package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateAccountUseCase

class CreateAccountViewModel(
    private val walletId: Long,
    private val createAccount: CreateAccountUseCase
) : ViewModel() {
    suspend fun createAccount(displayName: String, type: AccountType, symbol: String, params: String? = null) =
        createAccount(walletId, displayName, type, symbol, params)
}
