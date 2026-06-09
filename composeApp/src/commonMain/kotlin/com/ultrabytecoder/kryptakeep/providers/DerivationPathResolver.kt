package com.ultrabytecoder.kryptakeep.providers

import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.AccountType

object DerivationPathResolver {

    fun defaultPath(
        accountType: AccountType,
        accountIndex: Long,
        networkConfig: NetworkConfig
    ): String = when (accountType) {
        is AccountType.Btc -> "m/84'/${networkConfig.btcBip84CoinType}'/$accountIndex'"
        is AccountType.Eth -> "m/44'/60'/$accountIndex'/0/0"
        is AccountType.Erc20 -> "m/44'/60'/$accountIndex'/0/0"
        is AccountType.Trx -> "m/44'/195'/$accountIndex'/0/0"
        is AccountType.Trc20 -> "m/44'/195'/$accountIndex'/0/0"
        is AccountType.Ton -> "m/44'/607'/$accountIndex'"
    }

    fun expectedDepth(accountType: AccountType): Int = when (accountType) {
        is AccountType.Btc -> 3
        is AccountType.Ton -> 3
        else -> 5
    }

    fun isValidPath(path: String, accountType: AccountType): Boolean {
        val segments = parsePath(path)
        if (segments.isEmpty()) return false
        if (segments.size != expectedDepth(accountType)) return false
        return true
    }

    fun parsePath(path: String): List<Pair<Long, Boolean>> {
        if (!path.startsWith("m/")) return emptyList()
        val parts = path.removePrefix("m/").split("/")
        if (parts.isEmpty()) return emptyList()
        return parts.mapNotNull { part ->
            val hardened = part.endsWith("'")
            val numStr = if (hardened) part.removeSuffix("'") else part
            val num = numStr.toLongOrNull() ?: return@mapNotNull null
            if (num < 0) return@mapNotNull null
            num to hardened
        }
    }
}
