package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase

class CreateWalletViewModel(
    private val createWalletUseCase: CreateWalletUseCase
) : ViewModel() {
    sealed class Result {
        data class Success(val walletId: Long) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun createWallet(
        name: String,
        mnemonic: String,
        passphrase: String = ""
    ): Result = try {
        val walletId = createWalletUseCase(name, mnemonic, passphrase)
        Result.Success(walletId)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Invalid mnemonic")
    }
}
