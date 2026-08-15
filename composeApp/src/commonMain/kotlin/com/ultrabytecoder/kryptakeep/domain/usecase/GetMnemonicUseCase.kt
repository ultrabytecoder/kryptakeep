package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.security.wipe

class GetMnemonicUseCase(
    private val walletRepository: WalletRepository
) {
    /**
     * Returns the stored mnemonic as a [CharArray] (stored plaintext inside the
     * SQLCipher-encrypted database, protected at rest by the DEK). Returns null
     * when the mnemonic was never stored. The caller owns the returned array and
     * must wipe it when done.
     */
    suspend operator fun invoke(walletId: Long): CharArray? {
        val stored = walletRepository.getStoredMnemonic(walletId) ?: return null
        return try {
            CharArray(stored.size) { stored[it].toInt().toChar() }
        } finally {
            stored.wipe()
        }
    }
}