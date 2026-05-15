package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultrabytecoder.kryptakeep.domain.model.WalletInfo
import com.ultrabytecoder.kryptakeep.domain.usecase.DeleteWalletUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetWalletsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.RenameWalletUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageWalletsViewModel(
    getWallets: GetWalletsUseCase,
    private val deleteWalletUseCase: DeleteWalletUseCase,
    private val renameWalletUseCase: RenameWalletUseCase
) : ViewModel() {

    val wallets: StateFlow<List<WalletInfo>> = getWallets()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteWallet(id: Long) {
        viewModelScope.launch {
            deleteWalletUseCase(id)
        }
    }

    fun renameWallet(id: Long, name: String) {
        viewModelScope.launch {
            renameWalletUseCase(id, name)
        }
    }
}
