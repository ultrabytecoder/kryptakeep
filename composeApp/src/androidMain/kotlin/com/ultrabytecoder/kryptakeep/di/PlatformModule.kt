package com.ultrabytecoder.kryptakeep.di

import com.ultrabytecoder.kryptakeep.data.DatabaseDriverFactory
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.data.SettingsStore
import com.ultrabytecoder.kryptakeep.security.DbSessionFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage(androidContext()) } bind SettingsStore::class
}

/** Platform driver factory wired into SessionManager (constructed lazily with the app context). */
internal actual fun platformDriverFactory(): DbSessionFactory = object : DbSessionFactory {
    private var cached: DatabaseDriverFactory? = null
    private fun factory(context: android.content.Context): DatabaseDriverFactory =
        cached ?: DatabaseDriverFactory(context).also { cached = it }

    override suspend fun createDriver(passphrase: ByteArray) =
        factory(org.koin.mp.KoinPlatform.getKoin().get()).createDriver(passphrase)

    override fun deleteDatabase() =
        factory(org.koin.mp.KoinPlatform.getKoin().get()).deleteDatabase()
}
