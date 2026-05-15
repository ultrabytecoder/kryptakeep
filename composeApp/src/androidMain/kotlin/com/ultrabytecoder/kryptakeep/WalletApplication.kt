package com.ultrabytecoder.kryptakeep

import android.app.Application
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.di.appModule
import com.ultrabytecoder.kryptakeep.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val networkConfig = if (BuildConfig.IS_TESTNET) NetworkConfig.testnet(BuildConfig.ETHERSCAN_API_KEY) else NetworkConfig.mainnet(BuildConfig.ETHERSCAN_API_KEY)

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MyApplication)
            modules(appModule(networkConfig), platformModule)
        }
    }
}
