package com.ultrabytecoder.kryptakeep.domain.provider

import com.ultrabytecoder.kryptakeep.domain.model.FiatCurrency

interface FiatQuoteProvider {
    suspend fun getPrice(cryptoSymbol: String, fiat: FiatCurrency): Double
}
