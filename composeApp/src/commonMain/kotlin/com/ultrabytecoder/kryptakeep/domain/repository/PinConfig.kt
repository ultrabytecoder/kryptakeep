package com.ultrabytecoder.kryptakeep.domain.repository

import kotlin.time.Duration.Companion.minutes

object PinConfig {
    const val LENGTH = 6
    const val MAX_ATTEMPTS = 5
    val INITIAL_LOCKOUT = 1.minutes
    val MAX_LOCKOUT = 60.minutes
    const val PBKDF2_ITERATIONS = 100_000
    const val SALT_SIZE = 32
}

/**
 * Result of a PIN verification attempt.
 */
sealed class VerifyResult {
    data object Success : VerifyResult()
    data class WrongPin(val remainingAttempts: Int) : VerifyResult()
    data class Locked(val lockedUntil: Long) : VerifyResult()
    data object Corrupted : VerifyResult()
}