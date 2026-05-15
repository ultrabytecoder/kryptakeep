package com.ultrabytecoder.kryptakeep.domain.model

data class AccountInfo(
    val id: String,
    val walletId: Long,
    val name: String,
    val amount: String,
    val type: AccountType,
    val symbol: String,
    val address: String?,
    val derivationIndex: Long,
    val params: String? = null
)
