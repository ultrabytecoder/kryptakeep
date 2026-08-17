package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetSecurityMethodUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import com.ultrabytecoder.kryptakeep.security.wipe
import com.ultrabytecoder.kryptakeep.ui.util.SecureTextFieldState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EnterPinState(
    val enteredPinLength: Int = 0,
    val errorMessage: String? = null,
    val isLocked: Boolean = false,
    val lockSecondsRemaining: Int = 0,
    val lockedUntil: Long = 0L,
    val isProcessing: Boolean = false,
    val shakeTriggerId: Int = 0,
    val securityMethod: SecurityMethod? = null
)

sealed class EnterPinEvent {
    data class NavigateToAccountsList(val walletId: Long) : EnterPinEvent()
    data object NavigateToCreateWallet : EnterPinEvent()
    data object NavigateToRecovery : EnterPinEvent()
}

/**
 * The entered credential is accumulated in a wipe-able buffer instead of
 * immutable Strings, so intermediate values never linger on the heap.
 */
class EnterPinViewModel(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val getWalletsUseCase: GetWalletsUseCase,
    private val syncUseCase: SyncUseCase,
    checkPinStatus: CheckPinStatusUseCase,
    getSecurityMethod: GetSecurityMethodUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(EnterPinState())
    val state: StateFlow<EnterPinState> = _state.asStateFlow()

    private val buffer = CharArray(PinConfig.PIN_LENGTH)
    private var bufferLength = 0
    private val passwordBuffer = SecureTextFieldState()

    private val _events = MutableSharedFlow<EnterPinEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: Flow<EnterPinEvent> = _events

    init {
        viewModelScope.launch {
            getSecurityMethod().collect { method ->
                _state.update { it.copy(securityMethod = method) }
            }
        }

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
    }

    /** Routes to the main screen, or to wallet creation when no wallet exists yet. */
    private suspend fun navigateAfterUnlock() {
        val walletId = getWalletsUseCase().first().firstOrNull()?.id
        if (walletId != null) {
            syncUseCase(viewModelScope, walletId, SyncMode.FULL)
            _events.emit(EnterPinEvent.NavigateToAccountsList(walletId))
        } else {
            _events.emit(EnterPinEvent.NavigateToCreateWallet)
        }
    }

    fun addDigit(digit: Char) {
        val s = _state.value
        if (s.isLocked) return
        if (s.isProcessing) return
        if (bufferLength >= PinConfig.PIN_LENGTH) return

        buffer[bufferLength++] = digit
        _state.update { it.copy(enteredPinLength = bufferLength, errorMessage = null) }

        if (bufferLength == PinConfig.PIN_LENGTH) {
            verifyPin()
        }
    }

    fun removeDigit() {
        if (bufferLength == 0) return
        buffer[--bufferLength] = '\u0000'
        _state.update { it.copy(enteredPinLength = bufferLength) }
    }

    fun onPasswordInput(text: String) {
        val s = _state.value
        if (s.isLocked) return
        if (s.isProcessing) return
        passwordBuffer.update(text)
        _state.update { it.copy(errorMessage = null) }
    }

    fun submitPassword() {
        val s = _state.value
        if (s.isLocked) return
        if (s.isProcessing) return
        val password = passwordBuffer.trimmedCopy()
        if (password.isEmpty()) return
        passwordBuffer.update("")
        _state.update { it.copy(isProcessing = true, enteredPinLength = 0) }
        viewModelScope.launch {
            try {
                when (val result = verifyPinUseCase(password)) {
                    is VerifyResult.Success -> {
                        _state.update { it.copy(isProcessing = false) }
                        navigateAfterUnlock()
                    }
                    is VerifyResult.WrongPin -> {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                enteredPinLength = 0,
                                shakeTriggerId = it.shakeTriggerId + 1,
                                errorMessage = "Incorrect password (${result.remainingAttempts} remaining)"
                            )
                        }
                    }
                    is VerifyResult.Locked -> {
                        val remaining = ((result.lockedUntil - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000)
                            .coerceAtLeast(0).toInt()
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                enteredPinLength = 0,
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
                        enteredPinLength = 0,
                        errorMessage = e.message ?: "Verification failed"
                    )
                }
            } finally {
                password.wipe()
            }
        }
    }

    private fun verifyPin() {
        val pin = buffer.copyOf()
        buffer.wipe()
        bufferLength = 0
        _state.update { it.copy(isProcessing = true, enteredPinLength = 0) }
        viewModelScope.launch {
            try {
                when (val result = verifyPinUseCase(pin)) {
                    is VerifyResult.Success -> {
                        _state.update { it.copy(isProcessing = false) }
                        navigateAfterUnlock()
                    }
                    is VerifyResult.WrongPin -> {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                enteredPinLength = 0,
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
                                enteredPinLength = 0,
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
                        enteredPinLength = 0,
                        errorMessage = e.message ?: "Verification failed"
                    )
                }
            } finally {
                pin.wipe()
            }
        }
    }

    override fun onCleared() {
        buffer.wipe()
        passwordBuffer.wipe()
        bufferLength = 0
        super.onCleared()
    }
}
