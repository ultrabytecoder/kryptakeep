package com.ultrabytecoder.kryptakeep.security

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.ultrabytecoder.kryptakeep.data.DatabaseDriverFactory
import com.ultrabytecoder.kryptakeep.data.DatabaseProvider
import com.ultrabytecoder.kryptakeep.db.KryptaKeepDatabase
import com.ultrabytecoder.kryptakeep.security.monotonicNowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of [SessionManager.unlock]. Distinguishes a successful unlock from a
 * failed unlock (wrong key / corrupt DB) from a transient lock request (the
 * session was locked while the driver was opening). The caller must handle
 * [Locked] differently from [Failed]: a transient lock is not a corruption and
 * should not route the user to recovery.
 */
sealed interface UnlockResult {
    /** The database was opened successfully; the session is now unlocked. */
    data object Success : UnlockResult

    /** The database could not be opened (wrong key, corrupt file, etc.). */
    data object Failed : UnlockResult

    /** A lock was requested while the driver was opening; the session remains
     * locked. The caller should prompt the user to try again, NOT route to
     * recovery. */
    data object Locked : UnlockResult
}

/**
 * Holds the session state: the raw DEK in memory and the opened SQLCipher database.
 *
 * Ownership of the DEK array:
 *  - On success, [unlock] takes ownership — the caller must NOT wipe it afterwards;
 *    it is wiped by [lock] (H-3: Memory Wiping).
 *  - On failure, [unlock] leaves the array untouched — the caller owns it and must wipe it.
 *
 * Idle timeout (F-5): after [IDLE_TIMEOUT_MS] without [registerActivity] the session
 * locks itself. [appScope] must outlive the session (application scope).
 *
 * Concurrency: [stateLock] (a coroutine [Mutex]) serializes ALL state mutations —
 * both [unlock]/[unlockRecreating] and [lock]. A single lock is used so that
 * [lock] cannot interleave with [unlock]: with two independent locks, a [lock]
 * running while [unlock] was inside the driver open could either leave the
 * session unlocked after a lock was requested, or close the freshly-opened driver
 * and wipe the DEK that [unlock] had just handed to the caller.
 *
 * The lock is held only for the short state-check and state-commit phases. The
 * suspending [databaseDriverFactory.createDriver] call runs OUTSIDE the lock.
 * While the lock is released during the driver open, [lock] cannot run (it
 * suspends on the same mutex), so the race is still prevented: the commit phase
 * re-acquires the lock and re-checks [lockRequested] before publishing state.
 */
class SessionManager(
    private val databaseDriverFactory: DatabaseDriverFactory,
    private val appScope: CoroutineScope
) : DatabaseProvider {

    private companion object {
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        const val IDLE_CHECK_INTERVAL_MS = 1000L
    }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val stateLock = Mutex()

    private var dek: ByteArray? = null
    private var driver: SqlDriver? = null
    private var database: KryptaKeepDatabase? = null

    private var lastActivity: Long = 0L

    private var idleJob: Job? = null

    /**
     * Set by [lock] (and NOT reset until the next [unlock] Phase 1). When [unlock]
     * reaches Phase 3 (after the suspending driver-open in Phase 2), it re-checks
     * this flag: if a [lock] ran while the lock was released during Phase 2, the
     * flag is true and the commit aborts, honouring the lock request.
     *
     * The flag is cleared in Phase 1 of [unlock]/[unlockRecreating] (when the
     * session is confirmed locked), so a stale flag from a previous lock() cannot
     * cause a permanent lockout.
     */
    private var lockRequested = false

    /**
     * Records user activity, resetting the idle timeout. Uses the monotonic clock
     * (not wall-clock [System.currentTimeMillis]) so the timeout cannot be defeated
     * by moving the system clock backward — the same clock the lockout logic uses.
     */
    fun registerActivity() {
        lastActivity = monotonicNowMillis()
    }

    private fun startIdleWatcher() {
        idleJob?.cancel()
        idleJob = appScope.launch {
            while (true) {
                delay(IDLE_CHECK_INTERVAL_MS)
                if (_isUnlocked.value &&
                    monotonicNowMillis() - lastActivity >= IDLE_TIMEOUT_MS
                ) {
                    lock()
                    SessionLockNotifier.notifyLocked()
                }
            }
        }
    }

    /**
     * Opens the database with [dek] and keeps the key in memory for the session.
     *
     * Never deletes the database file: when the file cannot be opened with this key
     * (stale/corrupt database), unlock fails and the caller must route to the recovery
     * flow (PIN re-setup through the setup wizard), never to file deletion.
     */
    suspend fun unlock(dek: ByteArray): UnlockResult {
        // Phase 1: check state under the lock. Clear the lockRequested flag (a
        // stale flag from a previous lock() must not abort this unlock). If the
        // session is locked, also clear stale driver/database/dek from a previous
        // crashed unlock so Phase 3's closeInternal() doesn't double-close.
        stateLock.lock()
        try {
            if (_isUnlocked.value) {
                // Session already open (e.g. PIN re-verified from Settings): keep the
                // driver untouched so active flows survive, only refresh the key.
                this.dek?.wipe()
                this.dek = dek
                registerActivity()
                return UnlockResult.Success
            }
            // Session is locked — clear the flag and any stale state.
            lockRequested = false
            closeInternal()
            registerActivity()
        } finally {
            stateLock.unlock()
        }

        // Phase 2: open the driver OUTSIDE the lock (createDriver is suspending).
        // lock() cannot run here because it suspends on stateLock.
        val newDriver = try {
            databaseDriverFactory.createDriver(dek)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return UnlockResult.Failed
        }

        // Phase 3: commit under the lock. Re-check lockRequested: if lock() ran
        // while the lock was released during Phase 2, the flag is true and we
        // abort, honouring the lock request.
        stateLock.lock()
        try {
            if (lockRequested) {
                // A lock was requested while we were opening the driver — honour it.
                try { newDriver.close() } catch (_: Exception) {}
                return UnlockResult.Locked
            }
            val newDatabase = KryptaKeepDatabase(newDriver)
            probe(newDriver)
            closeInternal()
            driver = newDriver
            database = newDatabase
            this.dek = dek
            _isUnlocked.value = true
            startIdleWatcher()
            return UnlockResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try { newDriver.close() } catch (_: Exception) {}
            return UnlockResult.Failed
        } finally {
            stateLock.unlock()
        }
    }

    /**
     * Opens the database, deleting the file and recreating it when it cannot be opened
     * with [dek]. Must only be used from the PIN setup wizard (fresh setup / recovery),
     * where no wallet data is at stake — the flag is not exposed through the public
     * [unlock] API on purpose.
     */
    internal suspend fun unlockRecreating(dek: ByteArray): Boolean {
        // Phase 1: clear state under the lock. Do NOT return early if the session
        // is unlocked: setupPin generates a brand-new DEK, so the old database must
        // be closed and recreated. closeInternal() does not set _isUnlocked = false
        // (that is done explicitly in lock()), so set it here to keep the state
        // consistent while the driver is being recreated.
        stateLock.lock()
        try {
            lockRequested = false
            closeInternal()
            _isUnlocked.value = false
        } finally {
            stateLock.unlock()
        }

        // Phase 2: open the driver OUTSIDE the lock (createDriver is suspending).
        // If the first attempt fails, delete the database and retry once.
        var newDriver: SqlDriver? = null
        try {
            newDriver = databaseDriverFactory.createDriver(dek)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                databaseDriverFactory.deleteDatabase()
            } catch (_: Exception) {
                // deleteDatabase failed (e.g. file locked) — proceed to retry;
                // createDriver will fail if the file still can't be opened.
            }
            try {
                newDriver = databaseDriverFactory.createDriver(dek)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                return false
            }
        }

        // Phase 3: commit under the lock. Re-check lockRequested.
        stateLock.lock()
        try {
            val d = newDriver ?: return false
            if (lockRequested) {
                try { d.close() } catch (_: Exception) {}
                return false
            }
            val newDatabase = KryptaKeepDatabase(d)
            probe(d)
            closeInternal()
            driver = d
            database = newDatabase
            this.dek = dek
            _isUnlocked.value = true
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try { newDriver?.close() } catch (_: Exception) {}
            return false
        } finally {
            stateLock.unlock()
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
     * Locks the session: closes the DB connection, zeroes the DEK in memory (H-3)
     * and purges any in-process cache of the device hardware key (F-13).
     *
     * This is a non-suspending function called from the idle watcher coroutine and
     * from the main thread. It acquires the same [stateLock] as the suspending
     * [unlock]/[unlockRecreating], so a lock can never interleave with an in-flight
     * unlock (see the class-level concurrency note). The mutex is a coroutine
     * primitive, so the critical section runs in a child coroutine of [appScope]
     * (the caller is not blocked).
     */
    fun lock() {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            stateLock.withLock {
                // Set the flag and do NOT reset it: the next unlock() Phase 1 will
                // clear it. If we reset it here, an unlock() in Phase 3 (after the
                // lock was released during Phase 2) would see lockRequested == false
                // and publish the unlocked state, reopening the race.
                lockRequested = true
                closeInternal()
                HardwareKeyStore.purgeCache()
                idleJob?.cancel()
                idleJob = null
                _isUnlocked.value = false
            }
        }
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
