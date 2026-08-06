package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase

actual class DatabaseDriverFactory actual constructor(context: Any?) {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(KryptaKeepDatabase.Schema, "kryptakeep.db", foreignKeysOn = true)
    }
}
