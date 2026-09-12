package com.ultrabytecoder.kryptakeep.data

import com.ultrabytecoder.kryptakeep.domain.model.FiatCurrency
import com.ultrabytecoder.kryptakeep.domain.provider.FiatQuoteProvider
import com.ultrabytecoder.kryptakeep.domain.provider.MockFiatQuoteProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RemoteFiatQuoteProvider(
    private val networkConfig: NetworkConfig,
    createClient: () -> HttpClient = { HttpClient() },
    private val ttlMillis: Long = 30_000,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val fallback: FiatQuoteProvider = MockFiatQuoteProvider(),
) : FiatQuoteProvider {

    private val client: HttpClient = createClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private var cache: Map<String, Double> = emptyMap()
    private var loadedAt: Long = 0L

    override suspend fun getPrice(cryptoSymbol: String, fiat: FiatCurrency): Double {
        val snapshot = ensureLoaded()
        return snapshot["${cryptoSymbol.uppercase()}:${fiat.code}"]
            ?: fallback.getPrice(cryptoSymbol, fiat)
    }

    private suspend fun ensureLoaded(): Map<String, Double> = lock.withLock {
        if (cache.isNotEmpty() && clock() - loadedAt < ttlMillis) return@withLock cache

        try {
            val response = client.get("${networkConfig.exchangeRateApiBase}/api/v1/rates")
            if (response.status == HttpStatusCode.OK) {
                val parsed = json.parseToJsonElement(response.body<String>()).jsonArray
                    .mapNotNull { element ->
                        val obj = element.jsonObject
                        val src = obj["src"]?.jsonPrimitive?.content
                        val dest = obj["dest"]?.jsonPrimitive?.content
                        val rate = obj["rate"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        if (src != null && dest != null && rate != null) "$src:$dest" to rate else null
                    }
                    .toMap()
                if (parsed.isNotEmpty()) {
                    cache = parsed
                    loadedAt = clock()
                }
            }
        } catch (e: Exception) {
        }
        cache
    }
}
