package com.ultrabytecoder.kryptakeep

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.di.appModule
import com.ultrabytecoder.kryptakeep.di.platformModule
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun main() = application {
    val useMainnet = System.getProperty("kryptakeep.network") == "mainnet"
    val networkConfig = if (useMainnet) {
        NetworkConfig.mainnet(etherscanApiKey = System.getProperty("kryptakeep.etherscan.key", ""))
    } else {
        NetworkConfig.testnet(etherscanApiKey = System.getProperty("kryptakeep.etherscan.key", ""))
    }

    startKoin {
        logger(org.koin.core.logger.PrintLogger(Level.ERROR))
        modules(appModule(networkConfig), platformModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "KryptaKeep",
        state = androidx.compose.ui.window.rememberWindowState(
            width = 420.dp,
            height = 780.dp
        )
    ) {
        App()
    }
}