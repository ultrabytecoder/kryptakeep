package com.ultrabytecoder.kryptakeep.domain.usecase

import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.providers.DerivationPathResolver
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CreateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val networkConfig: NetworkConfig
) {
    /**
     * Creates a new native account and returns its ID.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        walletId: Long,
        displayName: String,
        type: AccountType,
        symbol: String,
        params: String? = null,
        derivationPath: String? = null
    ): String {
        require(type.isNative) { "Use AddTokenUseCase for tokens" }

        val maxIndex = accountRepository.getMaxAccountIndexByWalletAndAccountType(walletId, type.toDbCode())
        val accountIndex = (maxIndex ?: -1) + 1
        val accountNameIndex = accountIndex + 1

        val resolvedPath = derivationPath
            ?: DerivationPathResolver.defaultPath(type, accountIndex, networkConfig)

        require(DerivationPathResolver.isValidPath(resolvedPath, type, networkConfig)) {
            "Invalid derivation path for $type: $resolvedPath"
        }

        require(!accountRepository.existsByDerivationPath(walletId, resolvedPath)) {
            "An account with derivation path $resolvedPath already exists"
        }

        val id = Uuid.random().toString()
        val account = AccountInfo(
            id = id,
            walletId = walletId,
            name = "$displayName account $accountNameIndex",
            amount = "0",
            type = type,
            symbol = symbol,
            address = null,
            accountIndex = accountIndex,
            derivationPath = resolvedPath,
            params = params,
            parentAccountId = null
        )
        accountRepository.insertAccount(account)
        return id
    }
}
