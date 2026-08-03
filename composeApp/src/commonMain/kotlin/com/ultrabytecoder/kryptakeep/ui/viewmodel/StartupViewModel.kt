package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.repository.PinState
import com.ultrabytecoder.kryptakeep.domain.usecase.CheckPinStatusUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed class StartupState {
    data object Loading : StartupState()
    data object NeedsWallet : StartupState()
    data class NeedsPinSetup(val walletId: Long) : StartupState()
    data class NeedsPinUnlock(val walletId: Long, val isLocked: Boolean) : StartupState()
}

class StartupViewModel(
    getWallets: GetWalletsUseCase,
    private val syncUseCase: SyncUseCase,
    checkPinStatus: CheckPinStatusUseCase
) : ViewModel() {
    val walletsFlow: Flow<List<WalletInfo>> = getWallets()

    val state: StateFlow<StartupState> = combine(
        getWallets(),
        checkPinStatus()
    ) { wallets, pinState ->
        val w: List<WalletInfo> = wallets
        val ps: PinState = pinState
        val walletId = w.firstOrNull()?.id ?: return@combine StartupState.NeedsWallet

        when (ps) {
            PinState.Loading -> StartupState.Loading
            PinState.NotSetup -> StartupState.NeedsPinSetup(walletId)
            is PinState.Setup -> {
                if (ps.isCorrupted) {
                    StartupState.NeedsPinSetup(walletId)
                } else {
                    StartupState.NeedsPinUnlock(walletId, ps.isLocked)
                }
            }
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), StartupState.Loading)

    fun fullSync(walletId: Long) {
        syncUseCase(viewModelScope, walletId, SyncMode.FULL)
    }
}