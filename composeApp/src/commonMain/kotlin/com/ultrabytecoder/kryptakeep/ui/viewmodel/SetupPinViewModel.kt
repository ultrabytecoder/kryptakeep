package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.repository.PinConfig
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SetupPinUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SetupPinState(
    val enteredPin: String = "",
    val isConfirming: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val isLocked: Boolean = false,
    val lockSecondsRemaining: Int = 0
)

sealed class SetupPinEvent {
    data class NavigateToAccountsList(val walletId: Long) : SetupPinEvent()
}

class SetupPinViewModel(
    private val setupPinUseCase: SetupPinUseCase,
    private val getWalletsUseCase: GetWalletsUseCase,
    private val syncUseCase: SyncUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SetupPinState())
    val state: StateFlow<SetupPinState> = _state.asStateFlow()

    // Keep firstPin as a private var — never exposed in UI state
    private var firstPin: String = ""

    private val _events = MutableSharedFlow<SetupPinEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<SetupPinEvent> = _events

    fun addDigit(digit: String) {
        val s = _state.value
        if (s.isProcessing) return
        if (s.enteredPin.length >= PinConfig.LENGTH) return

        val newPin = s.enteredPin + digit

        if (newPin.length == PinConfig.LENGTH) {
            if (s.isConfirming) {
                confirmPin(newPin)
            } else {
                firstPin = newPin
                _state.update {
                    it.copy(
                        isConfirming = true,
                        enteredPin = "",
                        errorMessage = null
                    )
                }
            }
        } else {
            _state.update { it.copy(enteredPin = newPin, errorMessage = null) }
        }
    }

    fun removeDigit() {
        _state.update { s ->
            if (s.enteredPin.isEmpty()) s
            else s.copy(enteredPin = s.enteredPin.dropLast(1))
        }
    }

    private fun confirmPin(confirmPin: String) {
        if (confirmPin != firstPin) {
            _state.update {
                it.copy(
                    isConfirming = false,
                    enteredPin = "",
                    errorMessage = "PINs do not match. Try again."
                )
            }
            firstPin = ""
            return
        }

        _state.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val pinChars = confirmPin.toCharArray()
            try {
                setupPinUseCase(pinChars)
                val walletId = getWalletsUseCase().first().firstOrNull()?.id
                if (walletId != null) {
                    _state.update { it.copy(isProcessing = false) }
                    syncUseCase(viewModelScope, walletId, SyncMode.FULL)
                    _events.emit(SetupPinEvent.NavigateToAccountsList(walletId))
                } else {
                    _state.update {
                        it.copy(
                            isConfirming = false,
                            isProcessing = false,
                            enteredPin = "",
                            errorMessage = "Wallet not found"
                        )
                    }
                    firstPin = ""
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isConfirming = false,
                        isProcessing = false,
                        enteredPin = "",
                        errorMessage = e.message ?: "Failed to set PIN"
                    )
                }
                firstPin = ""
            } finally {
                pinChars.wipe()
            }
        }
    }
}