package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.ultrabytecoder.kryptakeep.security.AtomicCounter
import com.ultrabytecoder.kryptakeep.security.SessionManager

/**
 * Wraps the session's [SqlDriver] so every statement execution is bracketed by
 * SessionManager's in-flight query guard. The session-lock drain waits for the
 * guard to reach zero before closing the SQLCipher driver, which makes it safe
 * for background coroutines (sync workers, reactive flow re-executions) to keep
 * using a captured database reference across a lock request: their statements
 * either finish before the close, or fail fast after it — never mid-flight on a
 * freed native handle.
 *
 * Fail-fast gate: [markClosed] is invoked by SessionManager on this exact
 * wrapper before the delegate is closed. A statement that starts AFTER the
 * close (a background coroutine still holding a captured database reference)
 * sees [closed] and throws [IllegalStateException] instead of executing on a
 * freed native handle. The check is taken around the in-flight increment:
 * an increment that lands after the close is immediately rolled back, so the
 * drain can still observe zero.
 *
 * Statement-level guarding is the contract: a lock that lands BETWEEN
 * statements of an open transaction closes the driver mid-transaction, which
 * is safe — SQLite rolls back the open transaction on close, and the
 * transaction's remaining statements then fail with the closed-driver error.
 */
class GuardedSqlDriver(
    private val delegate: SqlDriver,
    private val sessionManager: SessionManager
) : SqlDriver {

    private val closed = AtomicCounter(0)

    /** Called by SessionManager on this wrapper before the delegate is closed. */
    internal fun markClosed() {
        closed.incrementAndGet()
    }

    /**
     * Increments the in-flight guard unless this wrapper is already closed.
     * Returns false (and leaves the counter untouched) when closed, so the
     * caller must fail fast instead of executing on a freed native handle.
     */
    private fun tryBeginQuery(): Boolean {
        if (closed.get() != 0) return false
        sessionManager.beginQuery()
        if (closed.get() != 0) {
            // The close landed between the two reads: roll back the increment
            // (no statement will run, so the drain can still reach zero) and
            // fail fast.
            sessionManager.endQuery()
            return false
        }
        return true
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> {
        if (!tryBeginQuery()) throw IllegalStateException("Database is locked")
        try {
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        } finally {
            sessionManager.endQuery()
        }
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
        if (!tryBeginQuery()) throw IllegalStateException("Database is locked")
        try {
            return delegate.execute(identifier, sql, parameters, binders)
        } finally {
            sessionManager.endQuery()
        }
    }

    override fun currentTransaction() = delegate.currentTransaction()

    override fun newTransaction() = delegate.newTransaction()

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) =
        delegate.addListener(*queryKeys, listener = listener)

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) =
        delegate.removeListener(*queryKeys, listener = listener)

    override fun notifyListeners(vararg queryKeys: String) =
        delegate.notifyListeners(*queryKeys)

    override fun close() {
        // Delegates to the raw driver. Only SessionManager holds this wrapper
        // (consumers receive KryptaKeepDatabase, not the driver), so close() is
        // reached exclusively from the session-lock drain path — after in-flight
        // queries have finished.
        delegate.close()
    }
}
