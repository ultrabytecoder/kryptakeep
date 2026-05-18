package com.ultrabytecoder.kryptakeep.providers.tron

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.TransactionDirection
import com.ultrabytecoder.kryptakeep.domain.model.TransactionInfo
import com.ultrabytecoder.kryptakeep.domain.model.TransactionStatus
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.providers.Provider
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import com.ultrabytecoder.kryptakeep.providers.TrxBase
import com.ultrabytecoder.kryptakeep.providers.base58ToHexAddress
import com.ultrabytecoder.kryptakeep.providers.hexToBase58Address
import com.ultrabytecoder.kryptakeep.providers.sunToTrx
import com.ultrabytecoder.kryptakeep.providers.trxToSun
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.secp256k1.Hex
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

class TrxProvider(
    masterKey: DeterministicWallet.ExtendedPrivateKey,
    private val accountRepository: AccountRepository,
    val params: JsonObject,
    networkConfig: NetworkConfig,
    private val transactionRepository: TransactionRepository,
    private val createClient: () -> HttpClient = { HttpClient() }
) : TrxBase(masterKey, networkConfig), Provider {

    override suspend fun getAddress(accountId: String): String {
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val key = deriveTrxKeyFromPath(account.derivationPath)
        return trxAddressFromDerivedKey(key)
    }

    override suspend fun sync(accountId: String, syncMode: SyncMode) {
        val rawBalance = balance(accountId)
        val normalized = sunToTrx(rawBalance).toPlainString()
        accountRepository.updateAmount(accountId, normalized)

        val address = getAddress(accountId)
        val transactions = fetchTransactions(address, accountId, syncMode)
        transactionRepository.upsertAll(transactions)
    }

    override suspend fun balance(accountId: String): BigDecimal {
        val address = getAddress(accountId)
        val client = createClient()
        try {
            val response: HttpResponse = client.post("${networkConfig.tronApiBase}/wallet/getaccount") {
                contentType(ContentType.Application.Json)
                setBody("""{"address":"$address","visible":true}""")
            }
            val body = response.body<String>()
            val json = Json.parseToJsonElement(body).jsonObject
            val balanceSun = json["balance"]?.jsonPrimitive?.long ?: 0L
            return BigDecimal.fromLong(balanceSun)
        } finally {
            client.close()
        }
    }

    override suspend fun estimateFee(accountId: String, amount: BigDecimal): BigDecimal {
        val address = getAddress(accountId)
        val client = createClient()
        try {
            val response: HttpResponse = client.post("${networkConfig.tronApiBase}/wallet/getaccountresource") {
                contentType(ContentType.Application.Json)
                setBody("""{"address":"$address","visible":true}""")
            }
            val body = response.body<String>()
            val json = Json.parseToJsonElement(body).jsonObject

            val freeNetLimit = json["freeNetLimit"]?.jsonPrimitive?.longOrNull ?: 0L
            val freeNetUsed = json["freeNetUsed"]?.jsonPrimitive?.longOrNull ?: 0L
            val freeNetRemaining = freeNetLimit - freeNetUsed

            val bandwidthNeeded = 300L
            if (freeNetRemaining >= bandwidthNeeded) {
                return BigDecimal.ZERO
            }

            val shortfall = bandwidthNeeded - freeNetRemaining
            val feeSun = shortfall * 1_000L
            return sunToTrx(BigDecimal.fromLong(feeSun))
        } finally {
            client.close()
        }
    }

    override suspend fun createTransaction(address: String, amount: BigDecimal, accountId: String): String {
        val sunAmount = trxToSun(amount)
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val fromKey = deriveTrxKeyFromPath(account.derivationPath)
        val fromAddress = getAddress(accountId)

        val client = createClient()
        try {
            val createResponse: HttpResponse = client.post("${networkConfig.tronApiBase}/wallet/createtransaction") {
                contentType(ContentType.Application.Json)
                setBody("""{"to_address":"$address","owner_address":"$fromAddress","amount":$sunAmount,"visible":true}""")
            }
            val createBody = createResponse.body<String>()
            val createJson = Json.parseToJsonElement(createBody).jsonObject

            check(!createJson.containsKey("Error")) { "Error creating transaction: ${createJson["Error"]}" }

            val txidHex = createJson["txID"]!!.jsonPrimitive.content
            val rawData = createJson["raw_data"]!!.jsonObject.toString()
            val rawDataHex = createJson["raw_data_hex"]!!.jsonPrimitive.content
            val signatureHex = signTronTransaction(Hex.decode(txidHex), fromKey)

            return """{"txid":"$txidHex","raw_data":$rawData,"raw_data_hex":"$rawDataHex","signature":["$signatureHex"],"visible":true}"""
        } finally {
            client.close()
        }
    }

    override suspend fun send(address: String, amount: BigDecimal, accountId: String): String {
        val broadcastBody = createTransaction(address, amount, accountId)

        val client = createClient()
        try {
            return broadcastSignedTransaction(client, broadcastBody)
        } finally {
            client.close()
        }
    }

    private suspend fun fetchTransactions(
        address: String,
        accountId: String,
        syncMode: SyncMode
    ): List<TransactionInfo> {
        val client = createClient()
        try {
            val limit = if (syncMode == SyncMode.FULL) 200 else 50
            val url = "${networkConfig.tronApiBase}/v1/accounts/$address/transactions?limit=$limit&order_by=block_timestamp,desc"
            val response: HttpResponse = client.get(url)
            val body = response.body<String>()
            val json = Json.parseToJsonElement(body).jsonObject
            val dataArray = json["data"]?.jsonArray ?: return emptyList()

            return dataArray.mapNotNull { element ->
                parseTrxTransaction(element.jsonObject, address, accountId)
            }
        } finally {
            client.close()
        }
    }

    private fun parseTrxTransaction(
        tx: JsonObject,
        myAddress: String,
        accountId: String
    ): TransactionInfo? {
        val txHash = tx["txID"]?.jsonPrimitive?.content ?: return null
        val rawData = tx["raw_data"]?.jsonObject ?: return null
        val contract = rawData["contract"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val value = contract["parameter"]?.jsonObject?.get("value")?.jsonObject ?: return null

        val amount = value["amount"]?.jsonPrimitive?.content ?: "0"
        val ownerHex = value["owner_address"]?.jsonPrimitive?.content ?: return null
        val toHex = value["to_address"]?.jsonPrimitive?.content ?: return null

        val timestamp = rawData["timestamp"]?.jsonPrimitive?.longOrNull
            ?: tx["block_timestamp"]?.jsonPrimitive?.longOrNull
            ?: return null

        val blockHeight = rawData["block_number"]?.jsonPrimitive?.longOrNull
        val retEntry = tx["ret"]?.jsonArray?.firstOrNull()?.jsonObject
        val contractRet = retEntry
            ?.get("contractRet")?.jsonPrimitive?.content

        val fee = retEntry?.get("fee")?.jsonPrimitive?.longOrNull?.toString()

        val status = when (contractRet) {
            "SUCCESS" -> TransactionStatus.CONFIRMED
            "REVERT" -> TransactionStatus.FAILED
            else -> TransactionStatus.PENDING
        }

        val myHex = base58ToHexAddress(myAddress)
        val direction = when {
            ownerHex.equals(myHex, ignoreCase = true) && toHex.equals(myHex, ignoreCase = true) -> TransactionDirection.SELF
            ownerHex.equals(myHex, ignoreCase = true) -> TransactionDirection.OUTGOING
            toHex.equals(myHex, ignoreCase = true) -> TransactionDirection.INCOMING
            else -> TransactionDirection.SELF
        }

        val counterparty = if (direction == TransactionDirection.OUTGOING) {
            hexToBase58Address(toHex)
        } else {
            hexToBase58Address(ownerHex)
        }

        return TransactionInfo(
            id = "${accountId}_${txHash}",
            accountId = accountId,
            txHash = txHash,
            direction = direction,
            amount = amount,
            fee = fee,
            timestamp = timestamp,
            status = status,
            counterpartyAddress = counterparty,
            blockHeight = blockHeight,
            chainData = null
        )
    }
}
