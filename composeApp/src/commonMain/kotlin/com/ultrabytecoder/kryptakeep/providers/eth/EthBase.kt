package com.ultrabytecoder.kryptakeep.providers

import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.PublicKey
import fr.acinq.secp256k1.Hex
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

abstract class EthBase(
    protected val masterKey: DeterministicWallet.ExtendedPrivateKey,
    protected val networkConfig: NetworkConfig
) {

    protected fun deriveEthKey(index: Long): DeterministicWallet.ExtendedPrivateKey {
        return masterKey.derivePrivateKey(
            listOf(
                DeterministicWallet.hardened(44),
                DeterministicWallet.hardened(60),
                DeterministicWallet.hardened(0),
                0L,
                index
            )
        )
    }

    protected fun ethAddressFromPublicKey(key: DeterministicWallet.ExtendedPrivateKey): String {
        val uncompressed = key.publicKey.toUncompressedBin()
        val pubKeyNoPrefix = uncompressed.copyOfRange(1, 65)
        val hash = keccak256(pubKeyNoPrefix)
        return "0x" + Hex.encode(hash.copyOfRange(12, 32))
    }

    protected suspend fun ethGetTransactionCount(client: HttpClient, address: String): Long {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_getTransactionCount","params":["$address","pending"],"id":1}""")
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        return json["result"]!!.jsonPrimitive.content.removePrefix("0x").toLong(16)
    }

    protected suspend fun ethGasPrice(client: HttpClient): Long {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_gasPrice","params":[],"id":1}""")
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        return json["result"]!!.jsonPrimitive.content.removePrefix("0x").toLong(16)
    }

    protected suspend fun ethMaxPriorityFeePerGas(client: HttpClient): Long {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_maxPriorityFeePerGas","params":[],"id":1}""")
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        return json["result"]!!.jsonPrimitive.content.removePrefix("0x").toLong(16)
    }

    protected suspend fun ethBaseFee(client: HttpClient): Long {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest",false],"id":1}""")
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        val result = json["result"]!!.jsonObject
        return result["baseFeePerGas"]!!.jsonPrimitive.content.removePrefix("0x").toLong(16)
    }

    protected suspend fun ethEstimateGas(
        client: HttpClient,
        from: String,
        to: String,
        data: String,
        gasFeeCap: Long,
        gasTipCap: Long
    ): Long {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                """{"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"$from","to":"$to","data":"$data","maxFeePerGas":"0x${gasFeeCap.toString(16)}","maxPriorityFeePerGas":"0x${gasTipCap.toString(16)}"},"latest"],"id":1}"""
            )
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        if (json.containsKey("error")) return 0L
        return json["result"]!!.jsonPrimitive.content.removePrefix("0x").toLong(16)
    }

    protected suspend fun ethSendRawTransaction(client: HttpClient, rawTx: String): String {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["$rawTx"],"id":1}""")
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        if (json.containsKey("error")) {
            val errorMsg = json["error"].toString()
            throw RuntimeException("Ethereum broadcast failed: $errorMsg")
        }
        return json["result"]!!.jsonPrimitive.content
    }

    protected suspend fun ethCall(client: HttpClient, to: String, data: String): String {
        val response: HttpResponse = client.post(networkConfig.ethRpcUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$to","data":"$data"},"latest"],"id":1}""")
        }
        val body = response.body<String>()
        val json = Json.parseToJsonElement(body).jsonObject
        return json["result"]!!.jsonPrimitive.content
    }

    protected fun findRecoveryId(
        sig: fr.acinq.bitcoin.ByteVector64,
        msgHash: ByteArray,
        expectedPubKey: PublicKey
    ): Int {
        for (recId in 0..1) {
            try {
                val recovered = Crypto.recoverPublicKey(sig, msgHash, recId)
                if (recovered == expectedPubKey) return recId
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalStateException("Could not determine recovery ID")
    }

    protected fun signEip155Transaction(
        nonce: Long,
        gasPrice: Long,
        gasLimit: Long,
        to: String,
        value: Long,
        data: ByteArray,
        fromKey: DeterministicWallet.ExtendedPrivateKey
    ): String {
        val unsignedRlp = rlpEncodeList(
            listOf(
                rlpEncodeLong(nonce),
                rlpEncodeLong(gasPrice),
                rlpEncodeLong(gasLimit),
                rlpEncodeAddress(to),
                rlpEncodeLong(value),
                rlpEncode(data),
                rlpEncodeLong(networkConfig.ethChainId),
                rlpEncode(byteArrayOf()),
                rlpEncode(byteArrayOf())
            )
        )

        val msgHash = keccak256(unsignedRlp)
        val sig = Crypto.sign(msgHash, fromKey.privateKey)
        val recoveryId = findRecoveryId(sig, msgHash, fromKey.publicKey)
        val v = networkConfig.ethChainId * 2 + 35L + recoveryId

        val sigBytes = sig.toByteArray()
        val r = sigBytes.copyOfRange(0, 32)
        val s = sigBytes.copyOfRange(32, 64)

        val signedRlp = rlpEncodeList(
            listOf(
                rlpEncodeLong(nonce),
                rlpEncodeLong(gasPrice),
                rlpEncodeLong(gasLimit),
                rlpEncodeAddress(to),
                rlpEncodeLong(value),
                rlpEncode(data),
                rlpEncodeLong(v),
                rlpEncode(r),
                rlpEncode(s)
            )
        )

        return "0x" + Hex.encode(signedRlp)
    }

    /**
     * EIP-1559 DynamicFee transaction (type 0x02).
     * Matches Go's types.DynamicFeeTx signed with LondonSigner.
     */
    protected fun signEip1559Transaction(
        nonce: Long,
        gasTipCap: Long,
        gasFeeCap: Long,
        gasLimit: Long,
        to: String,
        value: Long,
        data: ByteArray,
        fromKey: DeterministicWallet.ExtendedPrivateKey
    ): String {
        val emptyAccessList = rlpEncodeList(emptyList())

        val unsignedPayload = rlpEncodeList(
            listOf(
                rlpEncodeLong(networkConfig.ethChainId),
                rlpEncodeLong(nonce),
                rlpEncodeLong(gasTipCap),
                rlpEncodeLong(gasFeeCap),
                rlpEncodeLong(gasLimit),
                rlpEncodeAddress(to),
                rlpEncodeLong(value),
                rlpEncode(data),
                emptyAccessList
            )
        )

        val unsignedTx = byteArrayOf(0x02) + unsignedPayload
        val msgHash = keccak256(unsignedTx)

        val sig = Crypto.sign(msgHash, fromKey.privateKey)
        val recoveryId = findRecoveryId(sig, msgHash, fromKey.publicKey)

        val sigBytes = sig.toByteArray()
        val r = sigBytes.copyOfRange(0, 32)
        val s = sigBytes.copyOfRange(32, 64)

        val signedPayload = rlpEncodeList(
            listOf(
                rlpEncodeLong(networkConfig.ethChainId),
                rlpEncodeLong(nonce),
                rlpEncodeLong(gasTipCap),
                rlpEncodeLong(gasFeeCap),
                rlpEncodeLong(gasLimit),
                rlpEncodeAddress(to),
                rlpEncodeLong(value),
                rlpEncode(data),
                emptyAccessList,
                rlpEncodeLong(recoveryId.toLong()),
                rlpEncode(r),
                rlpEncode(s)
            )
        )

        return "0x02" + Hex.encode(signedPayload)
    }
}
