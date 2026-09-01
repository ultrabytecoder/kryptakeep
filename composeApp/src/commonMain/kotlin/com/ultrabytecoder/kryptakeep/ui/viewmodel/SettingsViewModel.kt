package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ultrabytecoder.kryptakeep.data.SettingsKeys
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.domain.model.FiatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val settingsStorage: SettingsStorage
) : ViewModel() {

    private val _fiatCurrency = MutableStateFlow(
        FiatCurrency.fromCode(settingsStorage.getString(SettingsKeys.FIAT_CURRENCY))
    )
    val fiatCurrency: StateFlow<FiatCurrency> = _fiatCurrency.asStateFlow()

    fun setFiatCurrency(currency: FiatCurrency) {
        settingsStorage.putString(SettingsKeys.FIAT_CURRENCY, currency.code)
        _fiatCurrency.value = currency
    }
}