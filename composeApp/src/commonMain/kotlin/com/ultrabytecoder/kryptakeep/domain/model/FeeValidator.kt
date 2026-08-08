package com.ultrabytecoder.kryptakeep.domain.model

object FeeValidator {
    // BTC: 1..2000 sat/vB
    fun validateBtcFeeRate(feeRate: Long): Boolean = feeRate in 1L..2000L

    // ETH: 1..10000 Gwei for base fee, 1..1000 Gwei for priority fee
    fun validateEthMaxFee(maxFeeGwei: Long): Boolean = maxFeeGwei in 1L..10_000L
    fun validateEthPriorityFee(priorityFeeGwei: Long): Boolean = priorityFeeGwei in 1L..1_000L

    // TRC-20: 1..100 TRX (1_000_000..100_000_000 SUN)
    fun validateTrc20FeeLimit(feeLimitSun: Long): Boolean = feeLimitSun in 1_000_000L..100_000_000L
}