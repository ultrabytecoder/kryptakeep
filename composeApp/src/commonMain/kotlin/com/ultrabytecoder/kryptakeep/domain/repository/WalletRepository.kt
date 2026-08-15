package com.ultrabytecoder.kryptakeep.domain.repository

import com.ultrabytecoder.kryptakeep.domain.model.MnemonicRecord
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun getWalletsFlow(): Flow<List<WalletInfo>>
    suspend fun getWallet(id: Long): WalletInfo?
    suspend fun getMasterSeed(id: Long): ByteArray?
    suspend fun insertWallet(name: String, masterSeed: ByteArray, encryptedMnemonic: ByteArray?, mnemonicSalt: ByteArray?): Long
    suspend fun deleteWallet(id: Long)
    suspend fun getMnemonicRecord(id: Long): MnemonicRecord?
    suspend fun renameWallet(id: Long, name: String)
    suspend fun updateMnemonic(id: Long, encryptedMnemonic: ByteArray, mnemonicSalt: ByteArray)

    /**
     * Runs [transform] for every wallet that has a stored mnemonic, inside a single
     * database transaction, and writes the updated (encrypted, salt) pair back.
     * Returns the number of wallets updated. Used by the PIN change flow so the
     * mnemonic re-encryption is all-or-nothing.
     */
    suspend fun reencryptMnemonicsInTransaction(
        transform: (walletId: Long, record: MnemonicRecord) -> Pair<ByteArray, ByteArray>?
    ): Int
}
