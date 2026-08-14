package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.domain.repository.BiometricRepository
import com.ultrabytecoder.kryptakeep.domain.service.BiometricService
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EnterPinState(
    val enteredPin: String = "",
    val errorMessage: String? = null,
    val isLocked: Boolean = false,
    val lockSecondsRemaining: Int = 0,
    val lockedUntil: Long = 0L,
    val isProcessing: Boolean = false,
    val shakeTriggerId: Int = 0
)

sealed class EnterPinEvent {
    data class NavigateToAccountsList(val walletId: Long) : EnterPinEvent()
    data object NavigateToRecovery : EnterPinEvent()
}

class EnterPinViewModel(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val getWalletsUseCase: GetWalletsUseCase,
    private val syncUseCase: SyncUseCase,
    checkPinStatus: CheckPinStatusUseCase,
    private val biometricRepository: BiometricRepository,
    private val biometricService: BiometricService
) : ViewModel() {

    private val _state = MutableStateFlow(EnterPinState())
    val state: StateFlow<EnterPinState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EnterPinEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: Flow<EnterPinEvent> = _events

    private val biometricMutex = Mutex()

    init {
        // Observe repository pin state changes — recompute lock from lockedUntil each time
        viewModelScope.launch {
            checkPinStatus().collect { pinState ->
                when (pinState) {
                    is PinState.Setup -> {
                        if (pinState.isCorrupted) {
                            _events.tryEmit(EnterPinEvent.NavigateToRecovery)
                        } else {
                            val remaining = if (pinState.isLocked) {
                                ((pinState.lockedUntil - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000)
                                    .coerceAtLeast(0).toInt()
                            } else {
                                0
                            }
                            _state.update {
                                it.copy(
                                    isLocked = pinState.isLocked,
                                    lockSecondsRemaining = remaining,
                                    lockedUntil = pinState.lockedUntil
                                )
                            }
                        }
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                isLocked = false,
                                lockSecondsRemaining = 0
                            )
                        }
                    }
                }
            }
        }

        // Tick lock countdown every second — only while locked
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _state.update { s ->
                    if (s.isLocked && s.lockedUntil > 0L) {
                        val newRemaining = ((s.lockedUntil - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000)
                            .coerceAtLeast(0).toInt()
                        s.copy(
                            lockSecondsRemaining = newRemaining,
                            isLocked = newRemaining > 0
                        )
                    } else {
                        s
                    }
                }
            }
        }

        // Auto-trigger biometric unlock if enabled — only after PIN state is loaded and not locked
        viewModelScope.launch {
            biometricMutex.withLock {
                val firstState = checkPinStatus().first()
                if (firstState is PinState.Setup && !firstState.isLocked && biometricRepository.isBiometricEnabled.value) {
                    val token = biometricRepository.authenticate(biometricService)
                    try {
                        if (token != null) {
                            val walletId = getWalletsUseCase().first().firstOrNull()?.id
                            if (walletId != null) {
                                syncUseCase(viewModelScope, walletId, SyncMode.FULL)
                                _events.emit(EnterPinEvent.NavigateToAccountsList(walletId))
                            }
                        }
                    } finally {
                        token?.wipe()
                    }
                }
            }
        }
    }

    fun triggerBiometric() {
        viewModelScope.launch {
            if (_state.value.isLocked) return@launch
            if (!biometricRepository.isBiometricEnabled.value) return@launch
            biometricMutex.withLock {
                val token = biometricRepository.authenticate(biometricService)
                try {
                    if (token != null) {
                        val walletId = getWalletsUseCase().first().firstOrNull()?.id
                        if (walletId != null) {
                            syncUseCase(viewModelScope, walletId, SyncMode.FULL)
                            _events.emit(EnterPinEvent.NavigateToAccountsList(walletId))
                        }
                    }
                } finally {
                    token?.wipe()
                }
            }
        }
    }

    fun addDigit(digit: String) {
        val s = _state.value
        if (s.isLocked) return
        if (s.isProcessing) return
        if (s.enteredPin.length >= PinConfig.LENGTH) return

        val newPin = s.enteredPin + digit
        _state.update { it.copy(enteredPin = newPin, errorMessage = null) }

        if (newPin.length == PinConfig.LENGTH) {
            verifyPin(newPin)
        }
    }

    fun removeDigit() {
        _state.update { s ->
            if (s.enteredPin.isEmpty()) s
            else s.copy(enteredPin = s.enteredPin.dropLast(1))
        }
    }

    private fun verifyPin(pin: String) {
        _state.update { it.copy(isProcessing = true, enteredPin = "") }
        viewModelScope.launch {
            val pinChars = pin.toCharArray()
            try {
                when (val result = verifyPinUseCase(pinChars)) {
                    is VerifyResult.Success -> {
                        val walletId = getWalletsUseCase().first().firstOrNull()?.id
                        if (walletId != null) {
                            _state.update { it.copy(isProcessing = false) }
                            syncUseCase(viewModelScope, walletId, SyncMode.FULL)
                            _events.emit(EnterPinEvent.NavigateToAccountsList(walletId))
                        } else {
                            _state.update {
                                it.copy(
                                    isProcessing = false,
                                    errorMessage = "Wallet not found"
                                )
                            }
                        }
                    }
                    is VerifyResult.WrongPin -> {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                enteredPin = "",
                                shakeTriggerId = it.shakeTriggerId + 1,
                                errorMessage = "Incorrect PIN (${result.remainingAttempts} remaining)"
                            )
                        }
                    }
                    is VerifyResult.Locked -> {
                        val remaining = ((result.lockedUntil - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000)
                            .coerceAtLeast(0).toInt()
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                enteredPin = "",
                                isLocked = true,
                                lockedUntil = result.lockedUntil,
                                lockSecondsRemaining = remaining,
                                errorMessage = null
                            )
                        }
                    }
                    is VerifyResult.Corrupted -> {
                        _state.update { it.copy(isProcessing = false) }
                        _events.tryEmit(EnterPinEvent.NavigateToRecovery)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        enteredPin = "",
                        errorMessage = e.message ?: "Verification failed"
                    )
                }
            } finally {
                pinChars.wipe()
            }
        }
    }
}