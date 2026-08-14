package com.ultrabytecoder.kryptakeep.data

expect class SettingsStorage(context: Any? = null) {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
}

/**
 * Storage keys for per-chain custom RPC/API node overrides.
 * A missing value means "use the NetworkConfig default".
 */
object CustomNodeKeys {
    const val BTC = "custom_node_btc"
    const val ETH = "custom_node_eth"
    const val TRX = "custom_node_trx"
    const val TON = "custom_node_ton"
}

object SettingsKeys {
    const val FIAT_CURRENCY = "fiat_currency"
}