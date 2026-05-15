package com.ultrabytecoder.kryptakeep

import androidx.compose.ui.window.ComposeUIViewController
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.di.appModule
import com.ultrabytecoder.kryptakeep.di.platformModule
import org.koin.compose.KoinApplication
import platform.Foundation.NSBundle

fun MainViewController() = ComposeUIViewController {
    KoinApplication(
        application = {
            val apiKey = NSBundle.mainBundle.objectForInfoDictionaryKey("EtherscanApiKey") as? String ?: ""
            modules(appModule(NetworkConfig.testnet(apiKey)), platformModule)
        }
    ) {
        App()
    }
}