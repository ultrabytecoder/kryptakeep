package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.usecase.GetMnemonicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportMnemonicViewModel(
    walletId: Long,
    getMnemonic: GetMnemonicUseCase
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Loaded(val mnemonic: String) : State
        data class NotAvailable(val reason: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val mnemonic = getMnemonic(walletId)
                if (mnemonic != null) {
                    _state.value = State.Loaded(mnemonic)
                } else {
                    _state.value = State.NotAvailable("Mnemonic was not stored when this wallet was created.")
                }
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Failed to load mnemonic")
            }
        }
    }
}
