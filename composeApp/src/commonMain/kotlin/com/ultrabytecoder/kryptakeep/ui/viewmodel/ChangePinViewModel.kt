package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.repository.ChangePinResult
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.usecase.ChangePinUseCase
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChangePinViewModel(
    private val changePinUseCase: ChangePinUseCase
) : ViewModel() {

    enum class Stage { OldPin, NewPin, ConfirmNewPin }

    data class UiState(
        val stage: Stage = Stage.OldPin,
        val enteredLength: Int = 0,
        val isProcessing: Boolean = false,
        val errorMessage: String? = null
    )

    sealed class Event {
        data object NavigateBack : Event()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<Event> = _events.asSharedFlow()

    // The three PINs are accumulated in wipe-able CharArray buffers — never
    // immutable Strings, so the PINs do not linger on the heap (ARCH-1/SEC-1).
    private val oldPinBuffer = CharArray(PinConfig.LENGTH)
    private val newPinBuffer = CharArray(PinConfig.LENGTH)
    private val confirmBuffer = CharArray(PinConfig.LENGTH)
    private var bufferLength = 0

    fun addDigit(digit: Char) {
        val s = _state.value
        if (s.isProcessing) return
        if (bufferLength >= PinConfig.LENGTH) return

        activeBuffer()[bufferLength++] = digit
        _state.update { it.copy(enteredLength = bufferLength, errorMessage = null) }

        if (bufferLength == PinConfig.LENGTH) {
            advance()
        }
    }

    fun removeDigit() {
        val s = _state.value
        if (s.isProcessing || bufferLength == 0) return
        activeBuffer()[--bufferLength] = '\u0000'
        _state.update { it.copy(enteredLength = bufferLength) }
    }

    private fun activeBuffer(): CharArray = when (_state.value.stage) {
        Stage.OldPin -> oldPinBuffer
        Stage.NewPin -> newPinBuffer
        Stage.ConfirmNewPin -> confirmBuffer
    }

    private fun advance() {
        when (_state.value.stage) {
            Stage.OldPin -> {
                bufferLength = 0
                _state.update { it.copy(stage = Stage.NewPin, enteredLength = 0) }
            }
            Stage.NewPin -> {
                bufferLength = 0
                _state.update { it.copy(stage = Stage.ConfirmNewPin, enteredLength = 0) }
            }
            Stage.ConfirmNewPin -> submit()
        }
    }

    private fun submit() {
        val old = oldPinBuffer.copyOf()
        val fresh = newPinBuffer.copyOf()
        val confirm = confirmBuffer.copyOf()
        confirmBuffer.wipe()
        bufferLength = 0

        if (!fresh.contentEquals(confirm)) {
            old.wipe()
            fresh.wipe()
            confirm.wipe()
            _state.update {
                it.copy(
                    stage = Stage.NewPin,
                    enteredLength = 0,
                    errorMessage = "New PINs do not match. Try again."
                )
            }
            return
        }
        confirm.wipe()

        // NEW-14: changing to the same PIN would trigger a pointless full
        // re-encryption of every stored mnemonic and the DEK envelope.
        if (old.contentEquals(fresh)) {
            old.wipe()
            fresh.wipe()
            _state.update {
                it.copy(
                    stage = Stage.NewPin,
                    enteredLength = 0,
                    errorMessage = "New PIN must be different from the current PIN"
                )
            }
            return
        }

        _state.update { it.copy(isProcessing = true, enteredLength = 0) }
        viewModelScope.launch {
            try {
                when (val result = changePinUseCase(old, fresh)) {
                    ChangePinResult.Success -> _events.emit(Event.NavigateBack)
                    ChangePinResult.WrongOldPin -> resetAfterFailure(
                        "Current PIN is incorrect"
                    )
                    is ChangePinResult.Locked -> resetAfterFailure(
                        "Too many attempts. Try again later."
                    )
                    is ChangePinResult.Failed -> resetAfterFailure(result.message)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                resetAfterFailure(e.message ?: "Failed to change PIN")
            } finally {
                old.wipe()
                fresh.wipe()
            }
        }
    }

    /** Returns the UI to the start of the flow after a failed PIN change. */
    private fun resetAfterFailure(message: String) {
        _state.update {
            it.copy(
                stage = Stage.OldPin,
                enteredLength = 0,
                isProcessing = false,
                errorMessage = message
            )
        }
    }

    override fun onCleared() {
        oldPinBuffer.wipe()
        newPinBuffer.wipe()
        confirmBuffer.wipe()
        bufferLength = 0
        super.onCleared()
    }
}