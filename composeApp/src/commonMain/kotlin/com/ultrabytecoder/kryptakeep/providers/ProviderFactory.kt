package com.ultrabytecoder.kryptakeep.providers

import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.providers.ton.TonProvider
import com.ultrabytecoder.kryptakeep.providers.tron.Trc20TokenProvider
import com.ultrabytecoder.kryptakeep.providers.tron.TrxProvider
import fr.acinq.bitcoin.DeterministicWallet
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object ProviderFactory {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun create(
        type: AccountType,
        keyProvider: KeyProvider,
        walletId: Long,
        utxoRepository: UtxoRepository,
        accountRepository: AccountRepository,
        transactionRepository: TransactionRepository,
        networkConfig: NetworkConfig,
        params: String? = null
    ): Provider {
        val parsedParams = params?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
        val masterSeed = keyProvider.getMasterSeed(walletId)
        val masterKey = DeterministicWallet.generate(masterSeed)
        return when (type) {
            is AccountType.Btc -> BtcProvider(
                masterKey, utxoRepository, accountRepository, parsedParams, networkConfig,
                transactionRepository = transactionRepository,
                createClient = { HttpClient() }
            )
            is AccountType.Eth -> EthProvider(
                masterKey, accountRepository, parsedParams, networkConfig,
                transactionRepository = transactionRepository
            )
            is AccountType.Trx -> TrxProvider(masterKey, accountRepository, parsedParams, networkConfig, transactionRepository = transactionRepository)
            is AccountType.Ton -> TonProvider(masterSeed, accountRepository, parsedParams, networkConfig, transactionRepository = transactionRepository)
            is AccountType.Erc20 -> Erc20TokenProvider(
                masterKey, accountRepository, parsedParams, networkConfig,
                transactionRepository = transactionRepository
            )
            is AccountType.Trc20 -> Trc20TokenProvider(
                masterKey,
                accountRepository,
                parsedParams,
                networkConfig,
                transactionRepository = transactionRepository
            )
            // TODO: implement TonTokenProvider when Jetton support is added
            is AccountType.TonToken -> TonProvider(masterSeed, accountRepository, parsedParams, networkConfig, transactionRepository = transactionRepository)
        }
    }
}
