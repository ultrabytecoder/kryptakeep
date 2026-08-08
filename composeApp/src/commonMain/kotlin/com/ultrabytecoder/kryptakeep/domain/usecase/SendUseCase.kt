package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.providers.ProviderFactory

class SendUseCase(
    private val accountRepository: AccountRepository,
    private val utxoRepository: UtxoRepository,
    private val transactionRepository: TransactionRepository,
    private val keyProvider: KeyProvider,
    private val networkConfig: NetworkConfig
) {
    suspend operator fun invoke(
        accountId: String,
        address: String,
        amount: BigDecimal,
        feeParams: CustomFeeParams? = null
    ): String {
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        val provider = ProviderFactory.create(account.type, keyProvider, account.walletId, utxoRepository, accountRepository, transactionRepository, networkConfig, account.params)
        val rawTx = provider.createTransaction(address, amount, account.id, feeParams)
        return provider.broadcast(rawTx)
    }
}
