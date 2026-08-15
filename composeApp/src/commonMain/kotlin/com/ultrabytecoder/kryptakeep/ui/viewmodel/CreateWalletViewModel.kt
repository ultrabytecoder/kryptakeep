package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.usecase.CreateWalletUseCase
import com.ultrabytecoder.kryptakeep.security.wipe

class CreateWalletViewModel(
    private val createWalletUseCase: CreateWalletUseCase
) : ViewModel() {
    sealed class Result {
        data class Success(val walletId: Long) : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Accepts the secrets as wipe-able [CharArray]s (the caller owns them and they
     * are wiped here once the use case consumed them).
     */
    suspend fun createWallet(
        name: String,
        mnemonic: CharArray,
        passphrase: CharArray = CharArray(0),
        pin: CharArray
    ): Result {
        if (pin.size != PinConfig.LENGTH || !pin.all { it.isDigit() }) {
            return Result.Error("PIN must be ${PinConfig.LENGTH} digits")
        }
        return try {
            val walletId = createWalletUseCase(name, mnemonic, passphrase, pin)
            Result.Success(walletId)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Invalid mnemonic")
        } finally {
            mnemonic.wipe()
            passphrase.wipe()
            pin.wipe()
        }
    }
}