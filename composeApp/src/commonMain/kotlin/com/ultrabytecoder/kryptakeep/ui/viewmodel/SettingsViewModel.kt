package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.data.SettingsKeys
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.domain.model.FiatCurrency
import com.ultrabytecoder.kryptakeep.domain.repository.BiometricRepository
import com.ultrabytecoder.kryptakeep.domain.repository.VerifyResult
import com.ultrabytecoder.kryptakeep.domain.service.BiometricService
import com.ultrabytecoder.kryptakeep.domain.usecase.VerifyPinUseCase
import com.ultrabytecoder.kryptakeep.security.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsPinAction { EnableBiometric, DisableBiometric }

data class SettingsState(
    val showPinDialog: Boolean = false,
    val pinAction: SettingsPinAction = SettingsPinAction.EnableBiometric,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val biometricRepository: BiometricRepository,
    private val biometricService: BiometricService,
    private val settingsStorage: SettingsStorage
) : ViewModel() {

    val isBiometricEnabled = biometricRepository.isBiometricEnabled

    private val _fiatCurrency = MutableStateFlow(
        FiatCurrency.fromCode(settingsStorage.getString(SettingsKeys.FIAT_CURRENCY))
    )
    val fiatCurrency: StateFlow<FiatCurrency> = _fiatCurrency.asStateFlow()

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun setFiatCurrency(currency: FiatCurrency) {
        settingsStorage.putString(SettingsKeys.FIAT_CURRENCY, currency.code)
        _fiatCurrency.value = currency
    }

    fun toggleBiometric(enable: Boolean) {
        if (enable) {
            _state.update { it.copy(showPinDialog = true, pinAction = SettingsPinAction.EnableBiometric, errorMessage = null) }
        } else {
            _state.update { it.copy(showPinDialog = true, pinAction = SettingsPinAction.DisableBiometric, errorMessage = null) }
        }
    }

    fun verifyPinAndProceed(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }
            val pinChars = pin.toCharArray()
            try {
                when (val result = verifyPinUseCase(pinChars)) {
                is VerifyResult.Success -> {
                    val action = _state.value.pinAction
                    when (action) {
                        SettingsPinAction.EnableBiometric -> {
                            val enabled = biometricRepository.enableBiometric(biometricService)
                            if (enabled) {
                                _state.update { it.copy(showPinDialog = false, isProcessing = false) }
                            } else {
                                _state.update {
                                    it.copy(
                                        isProcessing = false,
                                        errorMessage = "Biometric setup failed or cancelled"
                                    )
                                }
                            }
                        }
                        SettingsPinAction.DisableBiometric -> {
                            biometricRepository.disableBiometric(biometricService)
                            _state.update { it.copy(showPinDialog = false, isProcessing = false) }
                        }
                    }
                }
                is VerifyResult.WrongPin -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Incorrect PIN (${result.remainingAttempts} remaining)"
                        )
                    }
                }
                is VerifyResult.Locked -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Too many attempts. Locked."
                        )
                    }
                }
                is VerifyResult.Corrupted -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "App data corrupted. Recovery required."
                        )
                    }
                }
            }
            } finally {
                pinChars.wipe()
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(showPinDialog = false, errorMessage = null) }
    }
}