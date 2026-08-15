package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.usecase.SetupPinUseCase
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SetupPinState(
    val enteredPinLength: Int = 0,
    val isConfirming: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val isLocked: Boolean = false,
    val lockSecondsRemaining: Int = 0
)

sealed class SetupPinEvent {
    data object NavigateToCreateWallet : SetupPinEvent()
}

/**
 * PIN setup runs FIRST in the startup wizard — before any wallet exists. After the PIN
 * is set, the session is open and the app always proceeds to wallet creation.
 *
 * The PIN is accumulated in a wipe-able [CharArray] buffer instead of immutable
 * Strings, so intermediate values never linger on the heap.
 */
class SetupPinViewModel(
    private val setupPinUseCase: SetupPinUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SetupPinState())
    val state: StateFlow<SetupPinState> = _state.asStateFlow()

    // First-entry PIN, kept as a private wipe-able buffer — never exposed in UI state.
    private val firstPin = CharArray(PinConfig.LENGTH)
    private val buffer = CharArray(PinConfig.LENGTH)
    private var bufferLength = 0

    private val _events = MutableSharedFlow<SetupPinEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<SetupPinEvent> = _events

    fun addDigit(digit: Char) {
        val s = _state.value
        if (s.isProcessing) return
        if (bufferLength >= PinConfig.LENGTH) return

        buffer[bufferLength++] = digit
        _state.update { it.copy(enteredPinLength = bufferLength, errorMessage = null) }

        if (bufferLength == PinConfig.LENGTH) {
            if (s.isConfirming) {
                confirmPin()
            } else {
                buffer.copyInto(firstPin)
                buffer.wipe()
                bufferLength = 0
                _state.update {
                    it.copy(
                        isConfirming = true,
                        enteredPinLength = 0,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun removeDigit() {
        if (bufferLength == 0) return
        buffer[--bufferLength] = '\u0000'
        _state.update { it.copy(enteredPinLength = bufferLength) }
    }

    private fun confirmPin() {
        val matches = buffer.contentEquals(firstPin)
        buffer.wipe()
        bufferLength = 0
        if (!matches) {
            firstPin.wipe()
            _state.update {
                it.copy(
                    isConfirming = false,
                    enteredPinLength = 0,
                    errorMessage = "PINs do not match. Try again."
                )
            }
            return
        }

        val pinChars = firstPin.copyOf()
        firstPin.wipe()
        _state.update { it.copy(isProcessing = true, enteredPinLength = 0) }
        viewModelScope.launch {
            try {
                setupPinUseCase(pinChars)
                _state.update { it.copy(isProcessing = false) }
                _events.emit(SetupPinEvent.NavigateToCreateWallet)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isConfirming = false,
                        isProcessing = false,
                        enteredPinLength = 0,
                        errorMessage = e.message ?: "Failed to set PIN"
                    )
                }
            } finally {
                pinChars.wipe()
            }
        }
    }

    override fun onCleared() {
        buffer.wipe()
        firstPin.wipe()
        bufferLength = 0
        super.onCleared()
    }
}