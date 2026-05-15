package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncManager
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountsListViewModel(
    initialWalletId: Long,
    private val getAccounts: GetAccountsUseCase,
    getWallets: GetWalletsUseCase,
    private val syncUseCase: SyncUseCase,
    syncManager: SyncManager
) : ViewModel() {

    val wallets: StateFlow<List<WalletInfo>> = getWallets()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedWalletId = MutableStateFlow(initialWalletId)
    val selectedWalletId: StateFlow<Long> = _selectedWalletId.asStateFlow()

    val syncingAccounts: StateFlow<Set<String>> = syncManager.syncingAccounts

    val accounts: StateFlow<List<AccountInfo>?> = _selectedWalletId
        .flatMapLatest { walletId ->
            syncUseCase(viewModelScope, walletId, SyncMode.NORMAL)
            getAccounts.byWallet(walletId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun selectWallet(walletId: Long) {
        _selectedWalletId.value = walletId
        syncUseCase(viewModelScope, walletId, SyncMode.FULL)
    }
}
