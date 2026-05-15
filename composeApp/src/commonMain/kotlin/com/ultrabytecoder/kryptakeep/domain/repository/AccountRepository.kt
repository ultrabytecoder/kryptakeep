package com.ultrabytecoder.kryptakeep.domain.repository

import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountsByWalletFlow(walletId: Long): Flow<List<AccountInfo>>
    suspend fun getAccount(id: String): AccountInfo?
    suspend fun getMaxDerivationIndexByWalletAndAccountType(walletId: Long, type: String): Long?
    suspend fun insertAccount(account: AccountInfo)
    suspend fun updateAmount(accountId: String, amount: String)
    suspend fun updateParams(accountId: String, params: String)
    suspend fun deleteAccount(id: String)
    suspend fun deleteAccountsByWallet(walletId: Long)
}
