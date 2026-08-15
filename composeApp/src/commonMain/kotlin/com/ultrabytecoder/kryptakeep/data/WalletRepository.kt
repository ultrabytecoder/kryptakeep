package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository as WalletRepositoryInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WalletRepository(private val databaseProvider: DatabaseProvider) : WalletRepositoryInterface {
    private val queries get() = databaseProvider.database().kryptaKeepDatabaseQueries

    override fun getWalletsFlow(): Flow<List<WalletInfo>> {
        return queries.selectAllWallets()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toWalletInfo() } }
    }

    override suspend fun getWallet(id: Long): WalletInfo? = withContext(Dispatchers.IO) {
        queries.selectWalletById(id).executeAsOneOrNull()?.toWalletInfo()
    }

    override suspend fun getMasterSeed(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        queries.selectWalletById(id).executeAsOneOrNull()?.master_seed
    }

    override suspend fun insertWallet(name: String, masterSeed: ByteArray, mnemonic: ByteArray?): Long = withContext(Dispatchers.IO) {
        queries.insertWallet(
            id = null,
            name = name,
            master_seed = masterSeed,
            mnemonic = mnemonic
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun deleteWallet(id: Long) {
        withContext(Dispatchers.IO) {
            queries.deleteWalletById(id)
        }
    }

    override suspend fun getStoredMnemonic(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        queries.selectWalletById(id).executeAsOneOrNull()?.mnemonic
    }

    override suspend fun renameWallet(id: Long, name: String) {
        withContext(Dispatchers.IO) {
            queries.renameWallet(name, id)
        }
    }

    private fun com.ultrabytecoder.kryptakeep.db.Wallets.toWalletInfo(): WalletInfo = WalletInfo(
        id = id,
        name = name
    )
}