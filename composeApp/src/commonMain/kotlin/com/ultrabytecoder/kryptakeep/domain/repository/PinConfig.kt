package com.ultrabytecoder.kryptakeep.domain.repository

import kotlin.time.Duration.Companion.minutes

expect object PinConfig {
    val PIN_LENGTH: Int
    val PIN_LENGTH_OPTIONS: List<Int>
    val PASSWORD_MIN_LENGTH: Int
    val PASSWORD_MIN_CLASSES: Int
    val MAX_ATTEMPTS: Int
    val INITIAL_LOCKOUT: kotlin.time.Duration
    val MAX_LOCKOUT: kotlin.time.Duration
    val PBKDF2_ITERATIONS: Int
    val SALT_SIZE: Int
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