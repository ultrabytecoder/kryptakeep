package com.ultrabytecoder.kryptakeep.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class AccountType(val type: String) {
    data object Btc : AccountType("BTC")
    data object Eth : AccountType("ETH")
    data object Trx : AccountType("TRX")
    data object Ton : AccountType("TON")
    data class Erc20(val tokenAddress: String) : AccountType("ERC20")
    data class Trc20(val tokenAddress: String) : AccountType("TRC20")

    fun toDbCode(): String = type

    fun toParamsJson(): String? = when (this) {
        is Erc20 -> """{"tokenAddress":"$tokenAddress"}"""
        is Trc20 -> """{"tokenAddress":"$tokenAddress"}"""
        else -> null
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromCode(code: String, params: String? = null): AccountType = when (code) {
            "BTC" -> Btc
            "ETH" -> Eth
            "TRX" -> Trx
            "TON" -> Ton
            "ERC20" -> {
                val tokenAddress = parseTokenAddress(params)
                Erc20(tokenAddress)
            }
            "TRC20" -> {
                val tokenAddress = parseTokenAddress(params)
                Trc20(tokenAddress)
            }
            else -> throw IllegalArgumentException("Unsupported account type: $code")
        }

        private fun parseTokenAddress(params: String?): String {
            val parsed: JsonObject? = params?.let { json.parseToJsonElement(it).jsonObject }
            return parsed?.get("tokenAddress")?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing tokenAddress in params")
        }
    }
}
