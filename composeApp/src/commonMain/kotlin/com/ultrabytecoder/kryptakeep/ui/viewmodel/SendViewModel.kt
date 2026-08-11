package com.ultrabytecoder.kryptakeep.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ultrabytecoder.kryptakeep.data.NetworkConfig
import com.ultrabytecoder.kryptakeep.data.SettingsStorage
import com.ultrabytecoder.kryptakeep.domain.model.AccountInfo
import com.ultrabytecoder.kryptakeep.domain.model.AccountType
import com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams
import com.ultrabytecoder.kryptakeep.domain.model.FeeEstimation
import com.ultrabytecoder.kryptakeep.domain.model.FeePresets
import com.ultrabytecoder.kryptakeep.domain.model.FeeValidator
import com.ultrabytecoder.kryptakeep.domain.repository.AccountRepository
import com.ultrabytecoder.kryptakeep.domain.repository.TransactionRepository
import com.ultrabytecoder.kryptakeep.domain.repository.UtxoRepository
import com.ultrabytecoder.kryptakeep.domain.service.KeyProvider
import com.ultrabytecoder.kryptakeep.domain.usecase.EstimateFeeUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.GetAccountsUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SendUseCase
import com.ultrabytecoder.kryptakeep.domain.usecase.SyncAccountUseCase
import com.ultrabytecoder.kryptakeep.providers.ProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FeeSelectionMode {
    data object Auto : FeeSelectionMode()
    data object Slow : FeeSelectionMode()
    data object Medium : FeeSelectionMode()
    data object Fast : FeeSelectionMode()
    data object Custom : FeeSelectionMode()

    fun name(): String = when (this) {
        is Auto -> "auto"
        is Slow -> "slow"
        is Medium -> "medium"
        is Fast -> "fast"
        is Custom -> "custom"
    }

    companion object {
        fun fromName(name: String): FeeSelectionMode = when (name) {
            "slow" -> Slow
            "medium" -> Medium
            "fast" -> Fast
            "custom" -> Custom
            else -> Auto
        }
    }
}

private object FeePreferenceKeys {
    const val MODE_PREFIX = "fee_mode_"
    const val BTC_RATE = "fee_custom_btc_rate"
    const val ETH_PRIORITY = "fee_custom_eth_priority"
    const val ETH_MAX = "fee_custom_eth_max"
    const val TRC20_LIMIT = "fee_custom_trc20_limit"
}

class SendViewModel(
    private val accountId: String,
    private val getAccounts: GetAccountsUseCase,
    private val send: SendUseCase,
    private val estimateFeeUseCase: EstimateFeeUseCase,
    private val syncAccount: SyncAccountUseCase,
    private val accountRepository: AccountRepository,
    private val utxoRepository: UtxoRepository,
    private val transactionRepository: TransactionRepository,
    private val keyProvider: KeyProvider,
    private val networkConfig: NetworkConfig,
    private val settingsStorage: SettingsStorage
) : ViewModel() {
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _fee = MutableStateFlow<FeeEstimation?>(null)
    val fee: StateFlow<FeeEstimation?> = _fee.asStateFlow()

    private val _feeError = MutableStateFlow<String?>(null)
    val feeError: StateFlow<String?> = _feeError.asStateFlow()

    private val _feePresets = MutableStateFlow<FeePresets?>(null)
    val feePresets: StateFlow<FeePresets?> = _feePresets.asStateFlow()

    private val _selectedFeeMode = MutableStateFlow<FeeSelectionMode>(FeeSelectionMode.Auto)
    val selectedFeeMode: StateFlow<FeeSelectionMode> = _selectedFeeMode.asStateFlow()

    // Custom fee inputs
    private val _customBtcFeeRate = MutableStateFlow(10L)
    val customBtcFeeRate: StateFlow<Long> = _customBtcFeeRate.asStateFlow()

    private val _customEthPriorityFee = MutableStateFlow(25L)
    val customEthPriorityFee: StateFlow<Long> = _customEthPriorityFee.asStateFlow()

    private val _customEthMaxFee = MutableStateFlow(35L)
    val customEthMaxFee: StateFlow<Long> = _customEthMaxFee.asStateFlow()

    private val _customTrc20FeeLimit = MutableStateFlow(35_000_000L)
    val customTrc20FeeLimit: StateFlow<Long> = _customTrc20FeeLimit.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    init {
        // Load saved fee preferences
        val savedMode = settingsStorage.getString(FeePreferenceKeys.MODE_PREFIX + accountId)
        _selectedFeeMode.value = FeeSelectionMode.fromName(savedMode ?: "auto")
        _customBtcFeeRate.value = (settingsStorage.getString(FeePreferenceKeys.BTC_RATE) ?: "10").toLongOrNull() ?: 10L
        _customEthPriorityFee.value = (settingsStorage.getString(FeePreferenceKeys.ETH_PRIORITY) ?: "25").toLongOrNull() ?: 25L
        _customEthMaxFee.value = (settingsStorage.getString(FeePreferenceKeys.ETH_MAX) ?: "35").toLongOrNull() ?: 35L
        _customTrc20FeeLimit.value = (settingsStorage.getString(FeePreferenceKeys.TRC20_LIMIT) ?: "35000000").toLongOrNull() ?: 35_000_000L

        viewModelScope.launch {
            _account.value = getAccounts.byId(accountId)
            _account.value?.let { acc ->
                try {
                    val provider = ProviderFactory.create(
                        acc.type, keyProvider, acc.walletId,
                        utxoRepository, accountRepository, transactionRepository,
                        networkConfig, acc.params
                    )
                    _feePresets.value = provider.feePresets(acc.id)
                } catch (_: Exception) {
                    // presets not available for this chain
                }
            }
        }
    }

    fun setSelectedFeeMode(mode: FeeSelectionMode) {
        _selectedFeeMode.value = mode
        _validationError.value = null
        settingsStorage.putString(FeePreferenceKeys.MODE_PREFIX + accountId, mode.name())
    }

    fun setCustomBtcFeeRate(rate: Long) {
        _customBtcFeeRate.value = rate
        settingsStorage.putString(FeePreferenceKeys.BTC_RATE, rate.toString())
    }

    fun setCustomEthFees(priorityFee: Long, maxFee: Long) {
        _customEthPriorityFee.value = priorityFee
        _customEthMaxFee.value = maxFee
        settingsStorage.putString(FeePreferenceKeys.ETH_PRIORITY, priorityFee.toString())
        settingsStorage.putString(FeePreferenceKeys.ETH_MAX, maxFee.toString())
    }

    fun setCustomTrc20FeeLimit(limit: Long) {
        _customTrc20FeeLimit.value = limit
        settingsStorage.putString(FeePreferenceKeys.TRC20_LIMIT, limit.toString())
    }

    fun validateCustomFee(): Boolean {
        val accountType = _account.value?.type ?: return true
        val parentChain = accountType.parentChain() ?: accountType

        return when (parentChain) {
            is AccountType.Btc -> {
                val rate = _customBtcFeeRate.value
                if (FeeValidator.validateBtcFeeRate(rate)) {
                    _validationError.value = null
                    true
                } else {
                    _validationError.value = "BTC fee rate must be between 1 and 2000 sat/vB"
                    false
                }
            }
            is AccountType.Eth -> {
                val p = _customEthPriorityFee.value
                val m = _customEthMaxFee.value
                if (FeeValidator.validateEthFeeParams(p, m)) {
                    _validationError.value = null
                    true
                } else {
                    val parts = mutableListOf<String>()
                    if (!FeeValidator.validateEthPriorityFee(p)) parts.add("priority fee: 1–1000 Gwei")
                    if (!FeeValidator.validateEthMaxFee(m)) parts.add("max fee: 1–10000 Gwei")
                    if (m < p) parts.add("max fee must be >= priority fee")
                    _validationError.value = "Valid ranges — " + parts.joinToString(", ")
                    false
                }
            }
            is AccountType.Trx -> {
                val limit = _customTrc20FeeLimit.value
                if (FeeValidator.validateTrc20FeeLimit(limit)) {
                    _validationError.value = null
                    true
                } else {
                    _validationError.value = "TRC-20 fee limit must be between 1 and 100 TRX"
                    false
                }
            }
            else -> {
                _validationError.value = null
                true
            }
        }
    }

    private fun buildFeeParams(): CustomFeeParams? {
        val mode = _selectedFeeMode.value
        val presets = _feePresets.value
        val accountType = _account.value?.type ?: return null
        val parentChain = accountType.parentChain() ?: accountType

        return when (mode) {
            is FeeSelectionMode.Auto -> null
            is FeeSelectionMode.Slow -> presets?.slow
            is FeeSelectionMode.Medium -> presets?.medium
            is FeeSelectionMode.Fast -> presets?.fast
            is FeeSelectionMode.Custom -> when (parentChain) {
                is AccountType.Btc -> CustomFeeParams.Btc(_customBtcFeeRate.value)
                is AccountType.Eth -> CustomFeeParams.Eth(
                    _customEthPriorityFee.value,
                    _customEthMaxFee.value
                )
                is AccountType.Trx -> CustomFeeParams.Trc20(_customTrc20FeeLimit.value)
                else -> null
            }
        }
    }

    /** Returns true if this account type supports fee selection. */
    fun supportsFeeSelection(): Boolean {
        val accountType = _account.value?.type ?: return false
        val parentChain = accountType.parentChain() ?: accountType
        return parentChain is AccountType.Btc || parentChain is AccountType.Eth ||
            parentChain is AccountType.Trx || parentChain is AccountType.Ton
    }

    fun estimateFee(amount: BigDecimal, recipientAddress: String? = null) {
        viewModelScope.launch {
            try {
                _feeError.value = null
                _fee.value = estimateFeeUseCase(accountId, amount, recipientAddress, buildFeeParams())
            } catch (e: IllegalArgumentException) {
                _fee.value = null
                _feeError.value = "Fee estimation failed: ${e.message ?: "invalid parameter"}"
            } catch (e: IllegalStateException) {
                _fee.value = null
                _feeError.value = e.message ?: "Fee estimation failed"
            } catch (e: Exception) {
                _fee.value = null
                _feeError.value = "Fee estimation failed: ${e.message ?: "network error"}"
            }
        }
    }

    fun clearFee() {
        _fee.value = null
        _feeError.value = null
    }

    suspend fun sendTransaction(address: String, amount: BigDecimal): String {
        if (_selectedFeeMode.value is FeeSelectionMode.Custom && !validateCustomFee()) {
            throw IllegalStateException("Invalid custom fee: ${_validationError.value}")
        }
        val txid = send(accountId, address, amount, buildFeeParams())
        viewModelScope.launch { syncAccount(accountId) }
        return txid
    }
}