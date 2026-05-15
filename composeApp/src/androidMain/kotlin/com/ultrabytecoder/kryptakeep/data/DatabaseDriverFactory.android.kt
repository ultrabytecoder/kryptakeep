package com.ultrabytecoder.kryptakeep.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase

actual class DatabaseDriverFactory actual constructor(context: Any?) {
    private val appContext = context as Context

    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(KryptaKeepDatabase.Schema, appContext, "kryptakeep.db")
    }
}
