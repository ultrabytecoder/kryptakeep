package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.sqliter.DatabaseFileContext
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

actual class DatabaseDriverFactory actual constructor(context: Any?) {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(KryptaKeepDatabase.Schema, "kryptakeep.db", foreignKeysOn = true)
        excludeDatabaseFromBackup()
        return driver
    }

    /**
     * Marks the SQLite database file (and its WAL/SHM/journal sidecar files,
     * via the containing directory) as excluded from iTunes/Finder and iCloud
     * device backups. The database holds the encrypted wallet seed and PIN data.
     */
    private fun excludeDatabaseFromBackup() {
        val databasePath = DatabaseFileContext.databasePath("kryptakeep.db", null)
        val databaseUrl = NSURL.fileURLWithPath(databasePath)
        databaseUrl.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        databaseUrl.deletingLastPathComponent?.let { parentUrl ->
            parentUrl.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
    }
}
