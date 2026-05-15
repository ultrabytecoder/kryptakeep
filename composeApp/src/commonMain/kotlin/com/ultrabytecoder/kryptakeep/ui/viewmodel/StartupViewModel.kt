package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncUseCase
import com.ultrabytecoder.kryptakeep.providers.SyncMode
import kotlinx.coroutines.flow.Flow

class StartupViewModel(
    getWallets: GetWalletsUseCase,
    private val syncUseCase: SyncUseCase
) : ViewModel() {
    val walletsFlow: Flow<List<WalletInfo>> = getWallets()

    fun fullSync(walletId: Long) {
        syncUseCase(viewModelScope, walletId, SyncMode.FULL)
    }
}
