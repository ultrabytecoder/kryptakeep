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

interface PinRepository {
    val pinStateFlow: StateFlow<PinState>
    suspend fun setupPin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): VerifyResult
    suspend fun resetLockState()
}