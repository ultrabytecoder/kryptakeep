package com.ultrabytecoder.kryptakeep.domain.repository

import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun getWalletsFlow(): Flow<List<WalletInfo>>
    suspend fun getWallet(id: Long): WalletInfo?
    suspend fun getMasterSeed(id: Long): ByteArray?
    suspend fun insertWallet(name: String, masterSeed: ByteArray, encryptedMnemonic: ByteArray? = null): Long
    suspend fun deleteWallet(id: Long)
    suspend fun getEncryptedMnemonic(id: Long): ByteArray?
    suspend fun renameWallet(id: Long, name: String)
}
