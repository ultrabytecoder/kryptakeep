package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.usecase.EstimateFeeUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SendUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SendViewModel(
    private val accountId: String,
    private val getAccounts: GetAccountsUseCase,
    private val send: SendUseCase,
    private val estimateFeeUseCase: EstimateFeeUseCase,
    private val syncAccount: SyncAccountUseCase
) : ViewModel() {
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _fee = MutableStateFlow<BigDecimal?>(null)
    val fee: StateFlow<BigDecimal?> = _fee.asStateFlow()

    init {
        viewModelScope.launch {
            _account.value = getAccounts.byId(accountId)
        }
    }

    fun estimateFee(amount: BigDecimal) {
        viewModelScope.launch {
            try {
                _fee.value = estimateFeeUseCase(accountId, amount)
            } catch (_: Exception) {
                _fee.value = null
            }
        }
    }

    suspend fun sendTransaction(address: String, amount: BigDecimal): String {
        val txid = send(accountId, address, amount)
        viewModelScope.launch { syncAccount(accountId) }
        return txid
    }
}
