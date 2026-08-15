package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.security.wipe

/**
 * Loan pattern for the master seed (H-3: Memory Wiping): the seed is read from the
 * database (hardware-key-wrapped at rest on top of SQLCipher, see
 * [com.ultrabytecoder.kryptakeep.security.SecretCipher]), handed to the signing
 * block, and zeroed immediately after — it lives in memory only for the
 * milliseconds a transaction is signed.
 */
class KeyProviderImpl(
    private val walletRepository: WalletRepository
) : KeyProvider {
    override suspend fun <T> withMasterSeed(walletId: Long, block: suspend (ByteArray) -> T): T {
        val seed = walletRepository.getMasterSeed(walletId)
            ?: throw IllegalArgumentException("Wallet not found: $walletId")

        return try {
            block(seed)
        } finally {
            seed.wipe()
        }
    }
}