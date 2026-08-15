package com.ultrabytecoder.kryptakeep.security

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.ultrabytecoder.kryptakeep.data.DatabaseDriverFactory
import com.ultrabytecoder.kryptakeep.data.DatabaseProvider
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the session state: the raw DEK in memory and the opened SQLCipher database.
 *
 * Ownership of the DEK array:
 *  - On success, [unlock] takes ownership — the caller must NOT wipe it afterwards;
 *    it is wiped by [lock] (H-3: Memory Wiping).
 *  - On failure, [unlock] leaves the array untouched — the caller owns it and must wipe it.
 */
class SessionManager(
    private val databaseDriverFactory: DatabaseDriverFactory
) : DatabaseProvider {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var dek: ByteArray? = null
    private var driver: SqlDriver? = null
    private var database: KryptaKeepDatabase? = null

    /**
     * Opens the database with [dek] and keeps the key in memory for the session.
     *
     * Never deletes the database file: when the file cannot be opened with this key
     * (stale/corrupt database), unlock fails and the caller must route to the recovery
     * flow (PIN re-setup through the setup wizard), never to file deletion.
     */
    suspend fun unlock(dek: ByteArray): Boolean {
        if (_isUnlocked.value) {
            // Session already open (e.g. PIN re-verified from Settings): keep the driver
            // untouched so active flows survive, only refresh the in-memory key.
            this.dek?.wipe()
            this.dek = dek
            return true
        }

        try {
            return openWith(dek, recreateOnFailure = false)
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Opens the database, deleting the file and recreating it when it cannot be opened
     * with [dek]. Must only be used from the PIN setup wizard (fresh setup / recovery),
     * where no wallet data is at stake — the flag is not exposed through the public
     * [unlock] API on purpose.
     */
    internal suspend fun unlockRecreating(dek: ByteArray): Boolean {
        if (_isUnlocked.value) {
            this.dek?.wipe()
            this.dek = dek
            return true
        }

        try {
            return openWith(dek, recreateOnFailure = true)
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun openWith(dek: ByteArray, recreateOnFailure: Boolean): Boolean {
        var newDriver: SqlDriver? = null
        try {
            newDriver = databaseDriverFactory.createDriver(dek)
            val newDatabase = KryptaKeepDatabase(newDriver)
            probe(newDriver)
            closeInternal()
            driver = newDriver
            database = newDatabase
            this.dek = dek
            _isUnlocked.value = true
            newDriver = null // ownership transferred
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            newDriver?.close()
            if (recreateOnFailure) {
                databaseDriverFactory.deleteDatabase()
                try {
                    val retryDriver = databaseDriverFactory.createDriver(dek)
                    val retryDatabase = KryptaKeepDatabase(retryDriver)
                    probe(retryDriver)
                    closeInternal()
                    driver = retryDriver
                    database = retryDatabase
                    this.dek = dek
                    _isUnlocked.value = true
                    return true
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    try {
                        driver?.close()
                    } catch (_: Exception) {
                    }
                    driver = null
                    database = null
                    return false
                }
            }
            return false
        }
    }

    /**
     * Forces the SQLCipher key check against the on-disk file. On Android the driver
     * opens lazily; a wrong key surfaces here ("file is not a database") instead of
     * on the first query. On iOS the driver factory already probes eagerly.
     */
    private fun probe(driver: SqlDriver) {
        driver.executeQuery(null, "PRAGMA user_version;", { QueryResult.Value(Unit) }, 0)
    }

    /**
     * Locks the session: closes the DB connection and zeroes the DEK in memory (H-3).
     */
    fun lock() {
        closeInternal()
        _isUnlocked.value = false
    }

    private fun closeInternal() {
        try {
            driver?.close()
        } catch (_: Exception) {
        }
        driver = null
        database = null
        dek?.wipe()
        dek = null
    }

    override fun database(): KryptaKeepDatabase =
        database ?: throw IllegalStateException("Database is locked")
}