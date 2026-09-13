package com.ultrabytecoder.kryptakeep.di

import com.ultrabytecoder.kryptakeep.security.DbSessionFactory

/**
 * The platform's encrypted-database driver factory, wired into SessionManager.
 * Constructed directly per platform (it holds only the DB path) instead of being
 * resolved through Koin, which keeps test overrides trivial.
 */
internal expect fun platformDriverFactory(): DbSessionFactory
