package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository as AccountRepositoryInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountRepository(database: KryptaKeepDatabase) : AccountRepositoryInterface {
    private val queries = database.kryptaKeepDatabaseQueries

    override fun getAccountsByWalletFlow(walletId: Long): Flow<List<AccountInfo>> {
        return queries.selectByWalletId(walletId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toAccountInfo() } }
    }

    override suspend fun getAccount(id: String): AccountInfo? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toAccountInfo()
    }

    override suspend fun insertAccount(account: AccountInfo) {
        withContext(Dispatchers.IO) {
            queries.insert(
                id = account.id,
                wallet_id = account.walletId,
                name = account.name,
                amount = account.amount,
                type = account.type.toDbCode(),
                address = account.address,
                derivation_index = account.derivationIndex,
                params = account.params ?: account.type.toParamsJson(),
                symbol = account.symbol
            )
        }
    }

    override suspend fun getMaxDerivationIndexByWalletAndAccountType(walletId: Long, type: String): Long? = withContext(Dispatchers.IO) {
        queries.maxDerivationIndexByWalletAndAccountType(walletId, type).executeAsOne().max_index
    }

    override suspend fun updateAmount(accountId: String, amount: String) {
        withContext(Dispatchers.IO) {
            queries.updateAmount(amount, accountId)
        }
    }

    override suspend fun updateParams(accountId: String, params: String) {
        withContext(Dispatchers.IO) {
            queries.updateParams(params, accountId)
        }
    }

    override suspend fun deleteAccount(id: String) {
        withContext(Dispatchers.IO) {
            queries.deleteById(id)
        }
    }

    override suspend fun deleteAccountsByWallet(walletId: Long) {
        withContext(Dispatchers.IO) {
            queries.deleteAccountsByWalletId(walletId)
        }
    }

    private fun com.ultrabytecoder.kryptakeep.db.Accounts.toAccountInfo(): AccountInfo = AccountInfo(
        id = id,
        walletId = wallet_id,
        name = name,
        amount = amount,
        type = AccountType.fromCode(type, params),
        symbol = symbol,
        address = address,
        derivationIndex = derivation_index,
        params = params
    )
}
