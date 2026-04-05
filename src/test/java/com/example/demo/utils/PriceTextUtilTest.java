package com.example.demo.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PriceTextUtilTest {

    @Test
    void shouldKeepSinglePriceUnchanged() {
        Assertions.assertEquals("￥29.80", PriceTextUtil.normalizeDisplayPrice("￥29.80"));
    }

    @Test
    void shouldKeepLastPriceWhenRawTextContainsMultipleAmounts() {
        Assertions.assertEquals(
                "￥619.00", PriceTextUtil.normalizeDisplayPrice("￥779.30 ￥619.00"));
    }

    @Test
    void shouldExtractLastPriceFromMixedPromotionText() {
        Assertions.assertEquals(
                "￥224.40",
                PriceTextUtil.normalizeDisplayPrice("原价 ￥229.00 到手价 ￥224.40"));
    }

    @Test
    void shouldReturnCollapsedTextWhenNoAmountExists() {
        Assertions.assertEquals("暂无报价", PriceTextUtil.normalizeDisplayPrice("  暂无报价  "));
    }
}
