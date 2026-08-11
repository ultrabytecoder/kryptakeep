package com.ultrabytecoder.kryptakeep.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class FeeFormatUtilsTest {

    // --- formatFeeChipRate ---

    @Test
    fun formatFeeChipRate_nullReturnsNull() {
        assertNull(formatFeeChipRate(null))
    }

    @Test
    fun formatFeeChipRate_btc() {
        val params = com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams.Btc(10)
        assertEquals("10 sat/vB", formatFeeChipRate(params))
    }

    @Test
    fun formatFeeChipRate_eth() {
        val params = com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams.Eth(25, 35)
        assertEquals("35 Gwei", formatFeeChipRate(params))
    }

    @Test
    fun formatFeeChipRate_trc20() {
        val params = com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams.Trc20(35_000_000)
        assertEquals("35 TRX", formatFeeChipRate(params))
    }

    // --- formatFeeDetail ---

    @Test
    fun formatFeeDetail_nullReturnsNull() {
        assertNull(formatFeeDetail(null))
    }

    @Test
    fun formatFeeDetail_btc() {
        val params = com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams.Btc(10)
        val detail = formatFeeDetail(params)
        assertNotNull(detail)
        assertEquals(1, detail.size)
        assertEquals("Fee rate: 10 sat/vB", detail[0])
    }

    @Test
    fun formatFeeDetail_eth() {
        val params = com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams.Eth(25, 35)
        val detail = formatFeeDetail(params)
        assertNotNull(detail)
        assertEquals(2, detail.size)
        assertEquals("Max fee: 35 Gwei", detail[0])
        assertEquals("Priority tip: 25 Gwei", detail[1])
    }

    @Test
    fun formatFeeDetail_trc20() {
        val params = com.ultrabytecoder.kryptakeep.domain.model.CustomFeeParams.Trc20(35_000_000)
        val detail = formatFeeDetail(params)
        assertNotNull(detail)
        assertEquals(1, detail.size)
        assertEquals("Fee limit: 35 TRX (35000000 SUN)", detail[0])
    }
}