package com.ultrabytecoder.kryptakeep.ui.util

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams

private fun formatTrxValue(sun: Long): String {
    val trxValue = BigDecimal.fromLong(sun).divide(
        BigDecimal.fromLong(1_000_000),
        decimalMode = com.ionspin.kotlin.bignum.decimal.DecimalMode(
            decimalPrecision = 6,
            roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        )
    )
    return trxValue.toPlainString()
}

/**
 * Format [CustomFeeParams] into a short, human-readable string suitable for
 * display inside a fee-selection chip.
 *
 * Returns `null` when no params are available (e.g. Auto mode).
 */
fun formatFeeChipRate(params: CustomFeeParams?): String? {
    if (params == null) return null
    return when (params) {
        is CustomFeeParams.Btc -> "${params.feeRateSatVb} sat/vB"
        is CustomFeeParams.Eth -> "${params.maxFeePerGasGwei} Gwei"
        is CustomFeeParams.Trc20 -> "${formatTrxValue(params.feeLimitSun)} TRX"
    }
}

/**
 * Format [CustomFeeParams] into a list of detail lines for the
 * Network Fee card.  Returns `null` when no params are available.
 */
fun formatFeeDetail(params: CustomFeeParams?): List<String>? {
    if (params == null) return null
    return when (params) {
        is CustomFeeParams.Btc -> listOf("Fee rate: ${params.feeRateSatVb} sat/vB")
        is CustomFeeParams.Eth -> listOf(
            "Max fee: ${params.maxFeePerGasGwei} Gwei",
            "Priority tip: ${params.maxPriorityFeePerGasGwei} Gwei"
        )
        is CustomFeeParams.Trc20 -> listOf(
            "Fee limit: ${formatTrxValue(params.feeLimitSun)} TRX (${params.feeLimitSun} SUN)"
        )
    }
}