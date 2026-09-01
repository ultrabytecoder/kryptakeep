package com.ultrabytecoder.kryptakeep.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory actual constructor(context: Any?) {
    private val appContext = context as Context

    private companion object {
        const val DB_NAME = "kryptakeep.db"
    }

    actual suspend fun createDriver(passphrase: ByteArray): SqlDriver {
        System.loadLibrary("sqlcipher")
        return AndroidSqliteDriver(
            schema = KryptaKeepDatabase.Schema,
            context = appContext,
            name = DB_NAME,
            factory = SupportOpenHelperFactory(passphrase.copyOf())
        )
    }

    actual fun deleteDatabase() {
        appContext.deleteDatabase(DB_NAME)
    }
}