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

    /**
     * Startup wizard state — determines which wizard step to show at app launch.
     *
     * Wizard flow (linear; each step shown only if its precondition isn't met):
     *   Step 1: [StartupState.NeedsWallet]    → no wallet exists, navigate to CreateWallet
     *   Step 2: [StartupState.NeedsPinSetup]  → wallet exists but PIN not set, navigate to SetupPin
     *   Step 3: [StartupState.NeedsPinUnlock] → wallet + PIN exist, navigate to EnterPin
     *   Step 4: (handled by EnterPinViewModel) on correct PIN → AccountsList
     *
     * PIN setup is decoupled from wallet creation — it's a separate wizard step
     * that runs only once, regardless of how many wallets the user creates later.
     */
    val state: StateFlow<StartupState> = combine(
        getWallets(),
        checkPinStatus()
    ) { wallets, pinState ->
        determineWizardStep(wallets, pinState)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), StartupState.Loading)

    /**
     * Pure function that maps (wallets, pinState) → wizard step.
     */
    private fun determineWizardStep(
        wallets: List<WalletInfo>,
        pinState: PinState
    ): StartupState {
        val walletId = wallets.firstOrNull()?.id ?: return StartupState.NeedsWallet

        return when (pinState) {
            PinState.Loading -> StartupState.Loading
            PinState.NotSetup -> StartupState.NeedsPinSetup(walletId)
            is PinState.Setup -> {
                if (pinState.isCorrupted) {
                    StartupState.NeedsPinSetup(walletId)
                } else {
                    StartupState.NeedsPinUnlock(walletId, pinState.isLocked)
                }
            }
        }
    }

    fun fullSync(walletId: Long) {
        syncUseCase(viewModelScope, walletId, SyncMode.FULL)
    }
}