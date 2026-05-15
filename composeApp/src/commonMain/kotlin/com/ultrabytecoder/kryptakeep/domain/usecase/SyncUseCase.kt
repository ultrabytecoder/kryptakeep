package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.providers.ProviderFactory
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncUseCase(
    private val accountRepository: AccountRepository,
    private val utxoRepository: UtxoRepository,
    private val transactionRepository: TransactionRepository,
    private val keyProvider: KeyProvider,
    private val networkConfig: NetworkConfig,
    private val syncManager: SyncManager
) {
    operator fun invoke(scope: CoroutineScope, walletId: Long, syncMode: SyncMode = SyncMode.NORMAL) {
        scope.launch {
            println("Start syncing accounts of wallet $walletId (mode=$syncMode)")

            val accounts = accountRepository.getAccountsByWalletFlow(walletId).first()

            for (account in accounts) {
                launch {
                    if (!syncManager.tryAcquire(account.id)) {
                        println("Sync already in progress for ${account.id}, skipping")
                        return@launch
                    }
                    try {
                        val provider = ProviderFactory.create(account.type, keyProvider, account.walletId, utxoRepository, accountRepository, transactionRepository, networkConfig, account.params)
                        provider.sync(account.id, syncMode)
                    } catch (e: Exception) {
                        println("Sync failed for account ${account.id}: ${e.message}")
                    } finally {
                        syncManager.release(account.id)
                    }
                }
            }
        }
    }
}
