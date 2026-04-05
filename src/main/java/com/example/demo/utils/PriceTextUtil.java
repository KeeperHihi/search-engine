package com.example.demo.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一规整商品价格展示文本。
 * 抓取页和历史样例数据里都可能把原价、优惠价拼在同一个字段里，这里统一只保留最后一个金额。
 */
public final class PriceTextUtil {

    private static final Pattern PRICE_AMOUNT_PATTERN =
            Pattern.compile("[￥¥]?\\d+(?:\\.\\d{1,2})?");

    private PriceTextUtil() {
        // 工具类不需要实例化。
    }

    public static String normalizeDisplayPrice(String rawPrice) {
        if (rawPrice == null) {
            return "";
        }

        String normalizedText = rawPrice.trim().replaceAll("\\s+", " ");
        if (normalizedText.isEmpty()) {
            return "";
        }

        Matcher matcher = PRICE_AMOUNT_PATTERN.matcher(normalizedText);
        String lastMatchedPrice = "";
        while (matcher.find()) {
            lastMatchedPrice = normalizeAmountToken(matcher.group());
        }

        return lastMatchedPrice.isEmpty() ? normalizedText : lastMatchedPrice;
    }

    private static String normalizeAmountToken(String amountToken) {
        String numericPart = amountToken.replace("￥", "").replace("¥", "");
        if (numericPart.isEmpty()) {
            return "";
        }
        return "￥" + numericPart;
    }
}
