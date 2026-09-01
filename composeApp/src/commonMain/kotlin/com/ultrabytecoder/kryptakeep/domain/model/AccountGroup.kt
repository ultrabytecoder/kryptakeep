package com.ultrabytecoder.kryptakeep.domain.model

data class AccountGroup(
    val parent: AccountInfo,
    val tokens: List<AccountInfo>
)