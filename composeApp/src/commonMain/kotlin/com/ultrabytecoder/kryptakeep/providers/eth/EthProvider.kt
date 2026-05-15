package com.ultrabytecoder.kryptakeep.providers

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.TransactionDirection
import com.ultrabytecoder.kryptakeep.domain.model.TransactionInfo
import com.ultrabytecoder.kryptakeep.domain.model.TransactionStatus
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import fr.acinq.bitcoin.DeterministicWallet
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class EthProvider(
    masterKey: DeterministicWallet.ExtendedPrivateKey,
    private val accountRepository: AccountRepository,
    val params: JsonObject,
    networkConfig: NetworkConfig,
    private val createClient: () -> HttpClient = { HttpClient() },
    private val transactionRepository: TransactionRepository
) : EthBase(masterKey, networkConfig), Provider {

    override suspend fun getAddress(accountId: String): String {
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val key = deriveEthKey(account.derivationIndex)
        return ethAddressFromPublicKey(key)
    }

    override suspend fun sync(accountId: String, syncMode: SyncMode) {
        val rawBalance = balance(accountId)
        val normalized = rawBalance.divide(BigDecimal.fromLong(1_000_000_000_000_000_000)).toPlainString()
        accountRepository.updateAmount(accountId, normalized)

        val address = getAddress(accountId)
        val transactions = fetchTransactions(address, accountId, syncMode)
        transactionRepository.upsertAll(transactions)
    }

    override suspend fun balance(accountId: String): BigDecimal {
        val address = getAddress(accountId)
        val client = createClient()
        try {
            val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
                contentType(ContentType.Application.Json)
                setBody("""{"jsonrpc":"2.0","method":"eth_getBalance","params":["$address","latest"],"id":1}""")
            }
            val body = response.body<String>()
            val json = Json.parseToJsonElement(body).jsonObject
            val hexBalance = json["result"]!!.jsonPrimitive.content.removePrefix("0x")
            return BigDecimal.fromLong(hexBalance.toLong(16))
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
            val startBlock = when (syncMode) {
                SyncMode.FULL -> 0L
                SyncMode.NORMAL -> params["lastSyncBlock"]?.jsonPrimitive?.longOrNull ?: 0L
            }

            val url = "${networkConfig.ethEtherscanApiBase}" +
                "?chainid=${networkConfig.ethChainId}" +
                "&module=account&action=txlist" +
                "&address=$address" +
                "&startblock=$startBlock" +
                "&sort=desc" +
                "&apikey=${networkConfig.ethEtherscanApiKey}"

            val response: HttpResponse = client.get(url)
            val body = response.body<String>()
            val json = Json.parseToJsonElement(body).jsonObject

            val status = json["status"]?.jsonPrimitive?.content
            if (status != "1") return emptyList()

            val resultArray = json["result"]?.jsonArray ?: return emptyList()
            val transactions = resultArray.mapNotNull { parseEthTransaction(it.jsonObject, address, accountId) }

            // Update lastSyncBlock for incremental sync
            val maxBlock = resultArray.mapNotNull {
                it.jsonObject["blockNumber"]?.jsonPrimitive?.content?.toLongOrNull()
            }.maxOrNull()
            if (maxBlock != null && maxBlock > startBlock) {
                val updatedParams = JsonObject(params.toMutableMap() + ("lastSyncBlock" to JsonPrimitive(maxBlock)))
                accountRepository.updateParams(accountId, updatedParams.toString())
            }

            return transactions
        } finally {
            client.close()
        }
    }

    private fun parseEthTransaction(
        tx: JsonObject,
        myAddress: String,
        accountId: String
    ): TransactionInfo? {
        val hash = tx["hash"]?.jsonPrimitive?.content ?: return null
        val from = tx["from"]?.jsonPrimitive?.content ?: return null
        val to = tx["to"]?.jsonPrimitive?.content ?: return null
        val value = tx["value"]?.jsonPrimitive?.content ?: "0"
        val timeStamp = tx["timeStamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val blockNumber = tx["blockNumber"]?.jsonPrimitive?.content?.toLongOrNull()

        val gasUsed = tx["gasUsed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val gasPrice = tx["gasPrice"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val fee = (gasUsed * gasPrice).toString()

        val direction = when {
            from.equals(myAddress, ignoreCase = true) && to.equals(myAddress, ignoreCase = true) -> TransactionDirection.SELF
            from.equals(myAddress, ignoreCase = true) -> TransactionDirection.OUTGOING
            to.equals(myAddress, ignoreCase = true) -> TransactionDirection.INCOMING
            else -> TransactionDirection.SELF
        }

        val counterparty = if (direction == TransactionDirection.OUTGOING) to else from

        val isError = tx["isError"]?.jsonPrimitive?.content == "1"
        val txStatus = when {
            isError -> TransactionStatus.FAILED
            tx["txreceipt_status"]?.jsonPrimitive?.content == "0" -> TransactionStatus.FAILED
            else -> TransactionStatus.CONFIRMED
        }

        return TransactionInfo(
            id = "${accountId}_$hash",
            accountId = accountId,
            txHash = hash,
            direction = direction,
            amount = value,
            fee = fee,
            timestamp = timeStamp * 1000,  // Etherscan returns seconds, store as ms
            status = txStatus,
            counterpartyAddress = counterparty,
            blockHeight = blockNumber,
            chainData = null
        )
    }

    override suspend fun createTransaction(address: String, amount: BigDecimal, accountId: String): String {
        val weiAmount = amount.multiply(BigDecimal.fromLong(1_000_000_000_000_000_000)).longValue()
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val fromKey = deriveEthKey(account.derivationIndex)
        val fromAddress = ethAddressFromPublicKey(fromKey)

        val client = createClient()
        try {
            val nonce = ethGetTransactionCount(client, fromAddress)
            val gasTipCap = ethMaxPriorityFeePerGas(client)
            val baseFee = ethBaseFee(client)
            val gasFeeCap = baseFee + gasTipCap

            return signEip1559Transaction(
                nonce, gasTipCap, gasFeeCap, networkConfig.ethGasLimit, address, weiAmount, byteArrayOf(), fromKey
            )
        } finally {
            client.close()
        }
    }

    override suspend fun estimateFee(accountId: String, amount: BigDecimal): BigDecimal {
        val client = createClient()
        try {
            val gasTipCap = ethMaxPriorityFeePerGas(client)
            val baseFee = ethBaseFee(client)
            val gasFeeCap = baseFee + gasTipCap
            val feeWei = gasFeeCap * networkConfig.ethGasLimit
            return BigDecimal.fromLong(feeWei).divide(BigDecimal.fromLong(1_000_000_000_000_000_000))
        } finally {
            client.close()
        }
    }

    override suspend fun send(address: String, amount: BigDecimal, accountId: String): String {
        val rawTxHex = createTransaction(address, amount, accountId)

        val client = createClient()
        try {
            return ethSendRawTransaction(client, rawTxHex)
        } finally {
            client.close()
        }
    }
}
