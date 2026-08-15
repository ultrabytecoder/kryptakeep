package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetMnemonicUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.security.SessionLockNotifier
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExportMnemonicViewModel(
    private val walletId: Long,
    private val getMnemonic: GetMnemonicUseCase,
    private val verifyPinUseCase: VerifyPinUseCase,
    checkPinStatus: CheckPinStatusUseCase
) : ViewModel() {

    sealed interface State {
        data class AuthRequired(
            val enteredPinLength: Int = 0,
            val errorMessage: String? = null,
            val isLocked: Boolean = false,
            val lockSecondsRemaining: Int = 0,
            val lockedUntil: Long = 0L,
            val isProcessing: Boolean = false,
            val shakeTriggerId: Int = 0
        ) : State

        data object Loading : State
        data class Loaded(val mnemonic: CharArray) : State
        data class NotAvailable(val reason: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.AuthRequired())
    val state: StateFlow<State> = _state.asStateFlow()

    // Auth PIN buffer (wipe-able, never an immutable String).
    private val buffer = CharArray(PinConfig.LENGTH)
    private var bufferLength = 0

    init {
        viewModelScope.launch {
            checkPinStatus().collect { pinState ->
                when (pinState) {
                    is PinState.Setup -> {
                        if (pinState.isCorrupted) {
                            _state.value = State.Error("PIN data is corrupted. Wallet recovery is required.")
                        } else {
                            val remaining = if (pinState.isLocked) {
                                ((pinState.lockedUntil - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000)
                                    .coerceAtLeast(0).toInt()
                            } else {
                                0
                            }
                            updateAuth {
                                it.copy(
                                    isLocked = pinState.isLocked,
                                    lockSecondsRemaining = remaining,
                                    lockedUntil = pinState.lockedUntil
                                )
                            }
                        }
                    }
                    is PinState.NotSetup -> {
                        _state.value = State.Error("PIN is not set up. Enable it in Settings to view the recovery phrase.")
                    }
                    is PinState.Loading -> Unit
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateAuth { s ->
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

        // The session is locked when the app goes to the background: wipe any
        // displayed mnemonic immediately so it does not sit in memory while the
        // app is backgrounded (SESS-3).
        viewModelScope.launch {
            SessionLockNotifier.locked.collect {
                clearSensitiveData()
            }
        }
    }

    private fun updateAuth(transform: (State.AuthRequired) -> State.AuthRequired) {
        _state.update { s -> if (s is State.AuthRequired) transform(s) else s }
    }

    fun addDigit(digit: Char) {
        val s = _state.value as? State.AuthRequired ?: return
        if (s.isLocked || s.isProcessing) return
        if (bufferLength >= PinConfig.LENGTH) return

        buffer[bufferLength++] = digit
        updateAuth { it.copy(enteredPinLength = bufferLength, errorMessage = null) }

        if (bufferLength == PinConfig.LENGTH) {
            verifyPin()
        }
    }

    fun removeDigit() {
        if (bufferLength == 0) return
        buffer[--bufferLength] = '\u0000'
        updateAuth { it.copy(enteredPinLength = bufferLength) }
    }

    private fun verifyPin() {
        val pin = buffer.copyOf()
        buffer.wipe()
        bufferLength = 0
        updateAuth { it.copy(isProcessing = true, enteredPinLength = 0) }
        viewModelScope.launch {
            try {
                when (val result = verifyPinUseCase(pin)) {
                    is VerifyResult.Success -> loadMnemonic()
                    is VerifyResult.WrongPin -> updateAuth {
                        it.copy(
                            isProcessing = false,
                            shakeTriggerId = it.shakeTriggerId + 1,
                            errorMessage = "Incorrect PIN (${result.remainingAttempts} remaining)"
                        )
                    }
                    is VerifyResult.Locked -> {
                        val remaining = ((result.lockedUntil - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000)
                            .coerceAtLeast(0).toInt()
                        updateAuth {
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
                        _state.value = State.Error("PIN data is corrupted. Wallet recovery is required.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateAuth {
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

    /**
     * Loads the stored mnemonic (hardware-key-wrapped at rest) after a successful
     * PIN verification.
     */
    private suspend fun loadMnemonic() {
        val previous = _state.value
        if (previous is State.Loaded) {
            previous.mnemonic.wipe()
        }
        _state.value = State.Loading
        try {
            val mnemonic = getMnemonic(walletId)
            _state.value = if (mnemonic != null) {
                State.Loaded(mnemonic)
            } else {
                State.NotAvailable("Mnemonic was not stored when this wallet was created.")
            }
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Failed to load mnemonic")
        }
    }

    /**
     * Wipes any loaded mnemonic and resets the screen to the auth state.
     * Called by the UI when the screen is disposed, and automatically when the
     * session locks (app backgrounded).
     */
    fun clearSensitiveData() {
        val current = _state.value
        if (current is State.Loaded) {
            current.mnemonic.wipe()
        }
        buffer.wipe()
        bufferLength = 0
        _state.value = State.AuthRequired()
    }

    override fun onCleared() {
        clearSensitiveData()
        super.onCleared()
    }
}