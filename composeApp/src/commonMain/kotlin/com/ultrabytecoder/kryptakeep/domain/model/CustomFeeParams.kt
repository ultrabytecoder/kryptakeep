package com.ultrabytecoder.kryptakeep.domain.model

/**
 * User-selected fee parameters per chain.
 * [Default] means the provider picks fees automatically.
 */
sealed class CustomFeeParams {
    data class Btc(val feeRateSatVb: Long) : CustomFeeParams()
    data class Eth(
        val maxPriorityFeePerGasGwei: Long,
        val maxFeePerGasGwei: Long,
        val gasLimit: Long? = null
    ) : CustomFeeParams()
    data class Trc20(val feeLimitSun: Long) : CustomFeeParams()
}

/**
 * Fee presets returned by providers for slow/medium/fast.
 */
data class FeePresets(
    val slow: CustomFeeParams,
    val medium: CustomFeeParams,
    val fast: CustomFeeParams
)