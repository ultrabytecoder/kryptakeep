package com.ultrabytecoder.kryptakeep.providers.ton

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.domain.model.TransactionDirection
import com.ultrabytecoder.kryptakeep.domain.model.TransactionInfo
import com.ultrabytecoder.kryptakeep.domain.model.TransactionStatus
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.providers.Provider
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import com.ultrabytecoder.kryptakeep.providers.ton.address.TonAddress
import com.ultrabytecoder.kryptakeep.providers.ton.boc.*
import com.ultrabytecoder.kryptakeep.providers.ton.types.StateInit
import com.ultrabytecoder.kryptakeep.providers.ton.types.internalMessage
import com.ultrabytecoder.kryptakeep.providers.ton.types.storeStateInit
import com.ultrabytecoder.kryptakeep.providers.ton.wallet.WalletContract
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class TonProvider(
    masterSeed: ByteArray,
    private val accountRepository: AccountRepository,
    val params: JsonObject,
    networkConfig: NetworkConfig,
    private val transactionRepository: TransactionRepository,
    private val createClient: () -> HttpClient = { HttpClient() }
) : TonBase(masterSeed, networkConfig), Provider {

    override suspend fun getAddress(accountId: String): String {
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val keyPair = deriveTonKeyFromPath(account.derivationPath)
        val version = TonBase.parseWalletVersion(params)
        return tonAddressFromPublicKey(keyPair.publicKey, version)
    }

    override suspend fun sync(accountId: String, syncMode: SyncMode) {
        val rawBalance = balance(accountId)
        accountRepository.updateAmount(accountId, rawBalance.toPlainString())

        val address = getAddress(accountId)
        val transactions = fetchTransactions(address, accountId, syncMode)
        transactionRepository.upsertAll(transactions)
    }

    override suspend fun balance(accountId: String): BigDecimal {
        val account = accountRepository.getAccount(accountId) ?: return BigDecimal.ZERO
        val address = getAddress(accountId)
        val client = createClient()
        try {
            val nanotons = tonGetBalance(client, address)
            return BigDecimal.fromLong(nanotons).divide(BigDecimal.fromLong(networkConfig.tonNanotonsPerTon))
        } finally {
            client.close()
        }
    }

    override suspend fun estimateFee(accountId: String, amount: BigDecimal): BigDecimal {
        return BigDecimal.fromLong(10_000_000).divide(BigDecimal.fromLong(1_000_000_000))
    }

    override suspend fun createTransaction(address: String, amount: BigDecimal, accountId: String): String {
        val nanotons = amount.multiply(BigDecimal.fromLong(networkConfig.tonNanotonsPerTon)).longValue(exactRequired = false)
        val account = accountRepository.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val keyPair = deriveTonKeyFromPath(account.derivationPath)
        val version = TonBase.parseWalletVersion(params)
        val wallet: WalletContract = TonBase.walletContractFor(version, 0, keyPair.publicKey)
        val destAddress = TonAddress.parse(address)

        val client = createClient()
        try {
            val seqno = tonGetSeqno(client, wallet.address.toString())
            val destState = tonGetAccountState(client, address)
            val bounce = destState == "active" || destState == "frozen"

            val transferCell = wallet.createTransfer(
                seqno = seqno,
                secretKey = keyPair.privateKeySeed,
                messages = listOf(internalMessage(to = destAddress, value = nanotons, bounce = bounce)),
                sendMode = 3,
                timeout = null
            )

            val extMsgBuilder = beginCell()
                .storeUint(0b10, 2)        // ext_in_msg_info$10
                .storeAddress(null)         // src: addr_none
                .storeAddress(wallet.address) // dest: wallet address
                .storeCoins(0)              // import_fee

            // Include StateInit when wallet is not yet deployed (seqno == 0)
            if (seqno == 0) {
                val stateInit = StateInit(code = wallet.code, data = wallet.data)
                val stateInitCell = beginCell().storeWritable(storeStateInit(stateInit)).endCell()
                extMsgBuilder.storeBit(true)  // has init
                if (extMsgBuilder.availableBits - 1 >= stateInitCell.bits.length && extMsgBuilder.refsCount + stateInitCell.refs.size <= 3) {
                    extMsgBuilder.storeBit(false)
                    extMsgBuilder.storeSlice(stateInitCell.beginParse())
                } else {
                    extMsgBuilder.storeBit(true)
                    extMsgBuilder.storeRef(stateInitCell)
                }
            } else {
                extMsgBuilder.storeBit(false) // no state init
            }

            extMsgBuilder.storeBit(true)  // body as ref
                .storeRef(transferCell)

            val bodyCell = extMsgBuilder.endCell()

            val bocBytes = bodyCell.toBoc()
            return base64Encode(bocBytes)
        } finally {
            client.close()
        }
    }

    override suspend fun send(address: String, amount: BigDecimal, accountId: String): String {
        val bocBase64 = createTransaction(address, amount, accountId)
        val client = createClient()
        try {
            return tonSendBoc(client, bocBase64)
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
            val limit = if (syncMode == SyncMode.FULL) 100 else 20
            val url = "${networkConfig.tonApiBase}/getTransactions?address=$address&limit=$limit"
            val response: HttpResponse = client.get(url)
            val body = response.body<String>()
            val json = Json.parseToJsonElement(body).jsonObject

            if (json["ok"]?.jsonPrimitive?.content != "true") return emptyList()

            val resultArray = json["result"]?.jsonArray ?: return emptyList()

            return resultArray.mapNotNull { element ->
                parseTonTransaction(element.jsonObject, address, accountId)
            }
        } finally {
            client.close()
        }
    }

    private fun parseTonTransaction(
        tx: JsonObject,
        myAddress: String,
        accountId: String
    ): TransactionInfo? {
        val txId = tx["transaction_id"]?.jsonObject ?: return null
        val lt = txId["lt"]?.jsonPrimitive?.content ?: return null
        val hash = txId["hash"]?.jsonPrimitive?.content ?: return null
        val txHash = "$lt:$hash"

        val utime = tx["utime"]?.jsonPrimitive?.longOrNull ?: return null
        val timestamp = utime * 1000

        val inMsg = tx["in_msg"]?.jsonObject
        val inSource = inMsg?.get("source")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        val inDest = inMsg?.get("destination")?.jsonPrimitive?.contentOrNull
        val inValue = inMsg?.get("value")?.jsonPrimitive?.contentOrNull

        val outMsgs = tx["out_msgs"]?.jsonArray ?: emptyList()

        val hasExternalOutMsg = outMsgs.any { msg ->
            val dest = msg.jsonObject?.get("destination")?.jsonPrimitive?.contentOrNull
            dest != null && !dest.equals(myAddress, ignoreCase = true)
        }

        val direction = when {
            inSource != null && !inSource.equals(myAddress, ignoreCase = true) -> TransactionDirection.INCOMING
            hasExternalOutMsg -> TransactionDirection.OUTGOING
            else -> TransactionDirection.SELF
        }
        val amount = when (direction) {
            TransactionDirection.OUTGOING -> {
                outMsgs.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull ?: inValue ?: "0"
            }
            TransactionDirection.INCOMING -> inValue ?: "0"
            TransactionDirection.SELF -> inValue ?: "0"
        }

        val counterparty = when (direction) {
            TransactionDirection.OUTGOING -> outMsgs.firstOrNull()?.jsonObject?.get("destination")?.jsonPrimitive?.contentOrNull
            TransactionDirection.INCOMING -> inSource
            TransactionDirection.SELF -> null
        }

        val fee = tx["fee"]?.jsonPrimitive?.longOrNull?.toString()

        return TransactionInfo(
            id = "${accountId}_$lt",
            accountId = accountId,
            txHash = txHash,
            direction = direction,
            amount = amount,
            fee = fee,
            timestamp = timestamp,
            status = TransactionStatus.CONFIRMED,
            counterpartyAddress = counterparty,
            blockHeight = lt.toLongOrNull(),
            chainData = null
        )
    }
}
