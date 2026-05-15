package com.ultrabytecoder.kryptakeep.domain.model

data class UtxoInfo(
    val id: Long = 0,
    val accountId: String,
    val derivationPath: String,
    val amount: Long,
    val txid: String,
    val vout: Int
)
