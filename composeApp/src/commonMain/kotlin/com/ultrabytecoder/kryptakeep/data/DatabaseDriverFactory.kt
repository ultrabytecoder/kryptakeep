package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory(context: Any? = null) {
    fun createDriver(): SqlDriver
}
