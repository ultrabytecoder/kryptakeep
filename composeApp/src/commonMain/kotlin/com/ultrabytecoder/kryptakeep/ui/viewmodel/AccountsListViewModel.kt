package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.model.AccountGroup
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncManager
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    // Grouped accounts: native parents with their token children
    val accountGroups: StateFlow<List<AccountGroup>?> = _selectedWalletId
        .flatMapLatest { walletId ->
            getAccounts.byWallet(walletId).map { allAccounts ->
                val parents = allAccounts.filter { it.parentAccountId == null }
                parents.map { parent ->
                    val tokens = allAccounts.filter { it.parentAccountId == parent.id }
                    AccountGroup(parent = parent, tokens = tokens)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _syncMode = MutableStateFlow(SyncMode.NORMAL)

    init {
        viewModelScope.launch {
            _selectedWalletId.collect { walletId ->
                syncUseCase(viewModelScope, walletId, _syncMode.value)
                _syncMode.value = SyncMode.NORMAL
            }
        }
    }

    // Expanded state for account groups — defaults to all parents that have tokens
    private val _expandedOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val expandedAccountIds: StateFlow<Set<String>> = combine(
        accountGroups,
        _expandedOverrides
    ) { groups: List<AccountGroup>?, overrides: Map<String, Boolean> ->
        groups?.fold(emptySet<String>()) { ids, group ->
            val isExpanded = overrides[group.parent.id] ?: group.tokens.isNotEmpty()
            if (isExpanded) ids + group.parent.id else ids
        } ?: emptySet()
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun toggleExpanded(accountId: String) {
        _expandedOverrides.update { current ->
            val previous = current[accountId] ?: true
            if (previous) current - accountId else current + (accountId to true)
        }
    }

    fun selectWallet(walletId: Long) {
        _syncMode.value = SyncMode.FULL
        _selectedWalletId.value = walletId
    }
}
