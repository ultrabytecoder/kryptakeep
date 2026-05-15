package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.providers.ProviderFactory
import com.ultrabytecoder.kryptakeep.providers.SyncMode

class SyncAccountUseCase(
    private val accountRepository: AccountRepository,
    private val utxoRepository: UtxoRepository,
    private val transactionRepository: TransactionRepository,
    private val keyProvider: KeyProvider,
    private val networkConfig: NetworkConfig,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(accountId: String, syncMode: SyncMode = SyncMode.NORMAL) {
        if (!syncManager.tryAcquire(accountId)) {
            println("Sync already in progress for $accountId, skipping")
            return
        }
        try {
            val account = accountRepository.getAccount(accountId)
                ?: throw IllegalArgumentException("Account not found: $accountId")
            val provider = ProviderFactory.create(
                account.type, keyProvider, account.walletId, utxoRepository, accountRepository,
                transactionRepository, networkConfig, account.params
            )
            provider.sync(accountId, syncMode)
        } catch (e: Exception) {
            println("Sync failed for account $accountId: ${e.message}")
        } finally {
            syncManager.release(accountId)
        }
    }
}
