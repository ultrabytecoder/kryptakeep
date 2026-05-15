package com.ultrabytecoder.kryptakeep.domain.repository

import com.ultrabytecoder.kryptakeep.domain.model.UtxoInfo
import kotlinx.coroutines.flow.Flow

interface UtxoRepository {
    fun getUnspentByAccountFlow(accountId: String): Flow<List<UtxoInfo>>
    suspend fun getUtxosByAccount(accountId: String): List<UtxoInfo>
    suspend fun insertUtxo(utxo: UtxoInfo)

    suspend fun deleteUtxo(id: Long)
    suspend fun deleteUtxosByAccount(accountId: String)
}
