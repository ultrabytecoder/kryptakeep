package com.ultrabytecoder.kryptakeep.domain.repository

import kotlin.time.Duration.Companion.minutes

actual object PinConfig {
    actual val PIN_LENGTH = 6
    actual val PIN_LENGTH_OPTIONS = listOf(6, 8)
    actual val PASSWORD_MIN_LENGTH = 12
    actual val PASSWORD_MIN_CLASSES = 3
    actual val MAX_ATTEMPTS = 5
    actual val INITIAL_LOCKOUT = 1.minutes
    actual val MAX_LOCKOUT = 60.minutes
    actual val PBKDF2_ITERATIONS = 600_000
    actual val SALT_SIZE = 32
}
