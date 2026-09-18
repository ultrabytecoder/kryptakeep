package com.ultrabytecoder.kryptakeep.di

import com.ultrabytecoder.kryptakeep.data.DatabaseDriverFactory
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.data.SettingsStore
import com.ultrabytecoder.kryptakeep.security.DbSessionFactory
import org.koin.dsl.bind
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage() } bind SettingsStore::class
}

/** Platform driver factory wired into SessionManager (constructed directly —
 * it is stateless apart from the DB path, so no Koin indirection is needed). */
internal actual fun platformDriverFactory(): DbSessionFactory = object : DbSessionFactory {
    private val factory = DatabaseDriverFactory()
    override suspend fun createDriver(passphrase: ByteArray) = factory.createDriver(passphrase)
    override fun deleteDatabase() = factory.deleteDatabase()
}
