package com.ultrabytecoder.kryptakeep.providers

import com.ionspin.kotlin.bignum.decimal.BigDecimal

enum class SyncMode { FULL, NORMAL }

interface Provider {
    suspend fun getAddress(accountId: String): String
    suspend fun createTransaction(address: String, amount: BigDecimal, accountId: String): String
    suspend fun send(address: String, amount: BigDecimal, accountId: String): String
    suspend fun sync(accountId: String, syncMode: SyncMode = SyncMode.NORMAL) {}
    suspend fun balance(accountId: String): BigDecimal
    suspend fun estimateFee(accountId: String, amount: BigDecimal): BigDecimal
}
