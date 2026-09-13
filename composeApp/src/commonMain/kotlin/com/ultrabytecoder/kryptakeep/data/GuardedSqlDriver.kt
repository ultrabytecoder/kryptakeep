package com.ultrabytecoder.kryptakeep.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.ultrabytecoder.kryptakeep.security.SessionManager

/**
 * Wraps the session's [SqlDriver] so every statement execution is bracketed by
 * SessionManager's in-flight query guard. The session-lock drain waits for the
 * guard to reach zero before closing the SQLCipher driver, which makes it safe
 * for background coroutines (sync workers, reactive flow re-executions) to keep
 * using a captured database reference across a lock request: their statements
 * either finish before the close, or fail fast after it — never mid-flight on a
 * freed native handle.
 */
class GuardedSqlDriver(
    private val delegate: SqlDriver,
    private val sessionManager: SessionManager
) : SqlDriver {

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> {
        sessionManager.beginQuery()
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
        sessionManager.beginQuery()
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
