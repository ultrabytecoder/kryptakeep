package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.data.SettingsKeys
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.repository.SecurityMethod
import com.ultrabytecoder.kryptakeep.domain.usecase.SetupPinUseCase
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SetupPinState(
    val isChoosingLength: Boolean = true,
    val pinLength: Int = PinConfig.PIN_LENGTH,
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
 * The user first chooses the PIN length (6 or 8 digits); the choice is persisted in
 * [SettingsStorage] and used by every other PIN screen (unlock, change, export).
 *
 * The PIN is accumulated in a wipe-able [CharArray] buffer instead of immutable
 * Strings, so intermediate values never linger on the heap.
 */
class SetupPinViewModel(
    private val setupPinUseCase: SetupPinUseCase,
    private val settingsStorage: SettingsStorage
) : ViewModel() {

    private val _state = MutableStateFlow(SetupPinState())
    val state: StateFlow<SetupPinState> = _state.asStateFlow()

    // First-entry PIN, kept as a private wipe-able buffer — never exposed in UI state.
    // Allocated lazily once the user picks a length.
    private var firstPin: CharArray? = null
    private var buffer: CharArray? = null
    private var bufferLength = 0

    private val _events = MutableSharedFlow<SetupPinEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<SetupPinEvent> = _events

    fun selectPinLength(length: Int) {
        if (length !in PinConfig.PIN_LENGTH_OPTIONS) return
        val s = _state.value
        if (s.isProcessing) return
        settingsStorage.putString(SettingsKeys.PIN_LENGTH, length.toString())
        firstPin = CharArray(length)
        buffer = CharArray(length)
        bufferLength = 0
        _state.update {
            it.copy(
                isChoosingLength = false,
                pinLength = length,
                enteredPinLength = 0,
                isConfirming = false,
                errorMessage = null
            )
        }
    }

    fun addDigit(digit: Char) {
        val s = _state.value
        if (s.isChoosingLength || s.isProcessing) return
        val buf = buffer ?: return
        if (bufferLength >= s.pinLength) return

        buf[bufferLength++] = digit
        _state.update { it.copy(enteredPinLength = bufferLength, errorMessage = null) }

        if (bufferLength == s.pinLength) {
            if (s.isConfirming) {
                confirmPin()
            } else {
                firstPin?.let { buf.copyInto(it) }
                buf.wipe()
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
        val s = _state.value
        if (s.isChoosingLength || bufferLength == 0) return
        val buf = buffer ?: return
        buf[--bufferLength] = '\u0000'
        _state.update { it.copy(enteredPinLength = bufferLength) }
    }

    private fun confirmPin() {
        val buf = buffer ?: return
        val first = firstPin ?: return
        val matches = buf.contentEquals(first)
        buf.wipe()
        bufferLength = 0
        if (!matches) {
            first.wipe()
            _state.update {
                it.copy(
                    isConfirming = false,
                    enteredPinLength = 0,
                    errorMessage = "PINs do not match. Try again."
                )
            }
            return
        }

        val pinChars = first.copyOf()
        first.wipe()
        _state.update { it.copy(isProcessing = true, enteredPinLength = 0) }
        viewModelScope.launch {
            try {
                setupPinUseCase(pinChars, SecurityMethod.PIN)
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
        buffer?.wipe()
        firstPin?.wipe()
        bufferLength = 0
        super.onCleared()
    }
}
