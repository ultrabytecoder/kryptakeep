package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.FeeEstimation
import com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.providers.ProviderFactory
import kotlin.time.Clock

class EstimateFeeUseCase(
    private val accountRepository: AccountRepository,
    private val utxoRepository: UtxoRepository,
    private val transactionRepository: TransactionRepository,
    private val keyProvider: KeyProvider,
    private val networkConfig: NetworkConfig
) {
    // Simple in-memory cache to prevent API spam when user is typing amounts
    private var cachedEstimation: FeeEstimation? = null
    private var cacheKey: String? = null
    private var cacheTimestamp: Long = 0L
    private val cacheTtl = 30_000L // 30 seconds

    suspend operator fun invoke(
        accountId: String,
        amount: BigDecimal,
        recipientAddress: String? = null,
        feeParams: CustomFeeParams? = null
    ): FeeEstimation {
        val key = "$accountId|$amount|$recipientAddress|${feeParams?.hashCode()}"
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Return cached result if key matches and within TTL
        if (key == cacheKey && now - cacheTimestamp < cacheTtl) {
            return cachedEstimation ?: throw IllegalStateException("Cache invalid")
        }

        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        val estimation = keyProvider.withMasterSeed(account.walletId) { masterSeed ->
            val provider = ProviderFactory.create(
                account.type, masterSeed, utxoRepository, accountRepository,
                transactionRepository, networkConfig, account.params
            )
            provider.estimateFee(account.id, amount, recipientAddress, feeParams)
        }
        
        // Update cache with new result
        cachedEstimation = estimation
        cacheKey = key
        cacheTimestamp = now
        
        return estimation
    }
}
