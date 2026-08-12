package com.ultrabytecoder.kryptakeep.domain.model

/**
 * User-selected fee parameters per chain.
 * [Default] means the provider picks fees automatically.
 */
sealed class CustomFeeParams {
    abstract val chain: AccountType // Add this to enforce validation

    data class Btc(val feeRateSatVb: Long) : CustomFeeParams() {
        override val chain = AccountType.Btc
    }
    data class Eth(
        val maxPriorityFeePerGasGwei: Long,
        val maxFeePerGasGwei: Long,
        val gasLimit: Long? = null
    ) : CustomFeeParams() {
        override val chain = AccountType.Eth
    }
    data class Trc20(val feeLimitSun: Long) : CustomFeeParams() {
        override val chain = AccountType.Trx
    }
}

/**
 * Fee presets returned by providers for slow/medium/fast.
 */
data class FeePresets(
    val slow: CustomFeeParams,
    val medium: CustomFeeParams,
    val fast: CustomFeeParams
)