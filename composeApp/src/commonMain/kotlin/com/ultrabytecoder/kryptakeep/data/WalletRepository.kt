package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.repository.WalletRepository as WalletRepositoryInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WalletRepository(database: KryptaKeepDatabase) : WalletRepositoryInterface {
    private val queries = database.kryptaKeepDatabaseQueries

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

    override suspend fun insertWallet(name: String, masterSeed: ByteArray, encryptedMnemonic: ByteArray?): Long = withContext(Dispatchers.IO) {
        queries.insertWallet(
            id = null,
            name = name,
            master_seed = masterSeed,
            encrypted_mnemonic = encryptedMnemonic
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun deleteWallet(id: Long) {
        withContext(Dispatchers.IO) {
            queries.deleteWalletById(id)
        }
    }

    override suspend fun getEncryptedMnemonic(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        queries.selectEncryptedMnemonicById(id).executeAsOneOrNull()?.encrypted_mnemonic
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
