package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase
import com.ultrabytecoder.kryptakeep.security.wipe

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
    ): Result {
        val mnemonicChars = mnemonic.toCharArray()
        val passphraseChars = passphrase.toCharArray()
        return try {
            val walletId = createWalletUseCase(name, mnemonicChars, passphraseChars)
            Result.Success(walletId)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Invalid mnemonic")
        } finally {
            mnemonicChars.wipe()
            passphraseChars.wipe()
        }
    }
}