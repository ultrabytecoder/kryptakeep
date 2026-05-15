package com.ultrabytecoder.kryptakeep.data

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.BlockHash

data class NetworkConfig(
    val ethRpcUrl: String,
    val ethChainId: Long,
    val ethGasLimit: Long,
    val ethErc20GasLimit: Long,
    val ethEtherscanApiBase: String,
    val ethEtherscanApiKey: String,
    val tronApiBase: String,
    val trc20FeeLimit: Long,
    val tonApiBase: String,
    val tonNanotonsPerTon: Long,
    val btcMempoolApiBase: String,
    val btcBip84CoinType: Long,
    val btcGenesisBlockHash: BlockHash,
    val erc20Tokens: Map<String, TokenInfo>,
    val trc20Tokens: Map<String, TokenInfo>,
) {
    companion object {
        fun testnet(etherscanApiKey: String): NetworkConfig = NetworkConfig(
            ethRpcUrl = "https://ethereum-sepolia-rpc.publicnode.com",
            ethChainId = 11155111L,
            ethGasLimit = 21000L,
            ethErc20GasLimit = 60000L,
            ethEtherscanApiBase = "https://api.etherscan.io/v2/api",
            ethEtherscanApiKey = etherscanApiKey,
            tronApiBase = "https://nile.trongrid.io",
            trc20FeeLimit = 100_000_000L,
            tonApiBase = "http://10.0.2.2:8081",
            tonNanotonsPerTon = 1_000_000_000L,
            btcMempoolApiBase = "https://mempool.space/signet/api",
            btcBip84CoinType = 1L,
            btcGenesisBlockHash = Block.SignetGenesisBlock.hash,
            erc20Tokens = TestnetTokens.erc20Sepolia,
            trc20Tokens = TestnetTokens.trc20Nile,
        )

        fun mainnet(etherscanApiKey: String): NetworkConfig = NetworkConfig(
            ethRpcUrl = "https://ethereum-rpc.publicnode.com",
            ethChainId = 1L,
            ethGasLimit = 21000L,
            ethErc20GasLimit = 60000L,
            ethEtherscanApiBase = "https://api.etherscan.io/v2/api",
            ethEtherscanApiKey = etherscanApiKey,
            tronApiBase = "https://api.trongrid.io",
            trc20FeeLimit = 100_000_000L,
            tonApiBase = "https://toncenter.com/api/v2",
            tonNanotonsPerTon = 1_000_000_000L,
            btcMempoolApiBase = "https://mempool.space/api",
            btcBip84CoinType = 0L,
            btcGenesisBlockHash = Block.LivenetGenesisBlock.hash,
            erc20Tokens = MainnetTokens.erc20,
            trc20Tokens = MainnetTokens.trc20,
        )
    }
}
