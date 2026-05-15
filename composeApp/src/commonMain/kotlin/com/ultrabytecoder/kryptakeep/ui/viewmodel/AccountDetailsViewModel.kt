package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.TransactionInfo
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountAddressUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountDetailsViewModel(
    accountId: String,
    private val getAccounts: GetAccountsUseCase,
    private val getAccountAddress: GetAccountAddressUseCase,
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionInfo>>(emptyList())
    val transactions: StateFlow<List<TransactionInfo>> = _transactions.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val pageSize = 20L

    init {
        viewModelScope.launch {
            try {
                _account.value = getAccounts.byId(accountId)
                    ?: throw IllegalStateException("Account not found: $accountId")
                _address.value = getAccountAddress(accountId)
                loadNextPage(accountId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load account details"
            }
        }
    }

    fun loadNextPage() {
        val accountId = _account.value?.id ?: return
        viewModelScope.launch { loadNextPage(accountId) }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun loadNextPage(accountId: String) {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        try {
            val newItems = transactionRepository.getTransactionsByAccount(
                accountId, pageSize, _transactions.value.size.toLong()
            )
            _hasMore.value = newItems.size >= pageSize
            _transactions.value = _transactions.value + newItems
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load transactions"
        } finally {
            _isLoadingMore.value = false
        }
    }
}
