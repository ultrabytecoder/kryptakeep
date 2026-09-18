package com.ultrabytecoder.kryptakeep

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.di.appModule
import com.ultrabytecoder.kryptakeep.di.platformModule
import com.ultrabytecoder.kryptakeep.security.SessionManager
import com.ultrabytecoder.kryptakeep.security.installIdleHook
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level

fun main() = application {
    // Network is baked in at build time (DesktopBuildConfig, via -PkkNetwork).
    // A -Dkryptakeep.network=mainnet|testnet JVM property still overrides for dev.
    val useMainnet = when (System.getProperty("kryptakeep.network")) {
        "mainnet" -> true
        "testnet" -> false
        else -> !DesktopBuildConfig.IS_TESTNET
    }
    val etherscanKey = System.getProperty("kryptakeep.etherscan.key", DesktopBuildConfig.ETHERSCAN_API_KEY)
    val networkConfig = if (useMainnet) {
        NetworkConfig.mainnet(etherscanApiKey = etherscanKey)
    } else {
        NetworkConfig.testnet(etherscanApiKey = etherscanKey)
    }

    startKoin {
        logger(org.koin.core.logger.PrintLogger(Level.ERROR))
        modules(appModule(networkConfig), platformModule)
    }

    // User activity (mouse/keyboard) resets the session idle timeout (F-5).
    installIdleHook {
        runCatching {
            org.koin.core.context.GlobalContext.get().get<SessionManager>().registerActivity()
        }
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
