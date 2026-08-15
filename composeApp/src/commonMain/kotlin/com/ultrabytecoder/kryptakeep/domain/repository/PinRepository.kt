package com.ultrabytecoder.kryptakeep.domain.repository

import kotlinx.coroutines.flow.StateFlow

sealed interface PinState {
    data object Loading : PinState
    data object NotSetup : PinState
    data class Setup(
        val failedAttempts: Int,
        val lockedUntil: Long,
        val isCorrupted: Boolean = false
    ) : PinState {
        val isLocked: Boolean get() = kotlin.time.Clock.System.now().toEpochMilliseconds() < lockedUntil
    }
}

/**
 * Result of a PIN change attempt.
 */
sealed class ChangePinResult {
    data object Success : ChangePinResult()
    data object WrongOldPin : ChangePinResult()
    data class Locked(val lockedUntil: Long) : ChangePinResult()
    data class Failed(val message: String) : ChangePinResult()
}

interface PinRepository {
    val pinStateFlow: StateFlow<PinState>
    suspend fun setupPin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): VerifyResult
    suspend fun resetLockState()

    /**
     * Changes the PIN: verifies [oldPin], re-wraps the DEK envelope with [newPin]
     * and re-encrypts every stored mnemonic with [newPin] (all-or-nothing).
     */
    suspend fun changePin(oldPin: CharArray, newPin: CharArray): ChangePinResult
}