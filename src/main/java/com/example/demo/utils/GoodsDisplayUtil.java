package com.example.demo.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class GoodsDisplayUtil {

    public static final String SORT_DEFAULT = "default";
    public static final String SORT_POPULARITY = "popularity";
    public static final String SORT_NEWEST = "newest";
    public static final String SORT_SALES = "sales";
    public static final String SORT_PRICE = "price";

    public static final String ORDER_ASC = "asc";
    public static final String ORDER_DESC = "desc";

    private static final DecimalFormat COUNT_FORMAT = new DecimalFormat("0.0");

    private GoodsDisplayUtil() {}

    // 使用稳定种子生成“看起来随机、但同一商品始终一致”的展示指标，避免每次刷新数值跳变。
    public static void enrichGoods(Map<String, Object> sourceMap) {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return;
        }

        int stableSeed = buildStableSeed(sourceMap);
        int salesVolume = 500 + stableSeed % 9000;
        int popularityScore = 60 + stableSeed / 7 % 41;
        int commentCount = 200 + stableSeed / 11 % 50000;
        int favorableRate = 88 + stableSeed / 13 % 12;
        int listedDays = 1 + stableSeed / 17 % 45;

        sourceMap.put("salesVolume", salesVolume);
        sourceMap.put("salesText", formatCount(salesVolume));
        sourceMap.put("popularityScore", popularityScore);
        sourceMap.put("popularityText", popularityScore + "分");
        sourceMap.put("commentCount", commentCount);
        sourceMap.put("commentText", formatCount(commentCount));
        sourceMap.put("favorableRate", favorableRate);
        sourceMap.put("favorableRateText", favorableRate + "%");
        sourceMap.put("listedDays", listedDays);
        sourceMap.put("listedText", listedDays <= 3 ? "近3天上新" : listedDays + "天前上新");
        sourceMap.put("deliveryText", stableSeed % 3 == 0 ? "次日达" : "48小时内发货");
        sourceMap.put("shopName", stableSeed % 2 == 0 ? "京东自营" : "实践课旗舰店");
        sourceMap.put("featureTags", buildFeatureTags(stableSeed, salesVolume, popularityScore, listedDays));
    }

    // 前端排序条的排序能力统一放在这里，避免控制器和模板层各自维护一套规则。
    public static void sortGoods(
            List<Map<String, Object>> goodsList, String sortBy, String priceOrder) {
        if (goodsList == null || goodsList.size() < 2) {
            return;
        }

        if (SORT_POPULARITY.equals(sortBy)) {
            goodsList.sort(
                    Comparator.comparingInt((Map<String, Object> goods) -> readIntValue(goods, "popularityScore"))
                            .reversed());
            return;
        }

        if (SORT_NEWEST.equals(sortBy)) {
            goodsList.sort(Comparator.comparingInt(goods -> readIntValue(goods, "listedDays")));
            return;
        }

        if (SORT_SALES.equals(sortBy)) {
            goodsList.sort(
                    Comparator.comparingInt((Map<String, Object> goods) -> readIntValue(goods, "salesVolume"))
                            .reversed());
            return;
        }

        if (SORT_PRICE.equals(sortBy)) {
            goodsList.sort((leftGoods, rightGoods) -> comparePrice(leftGoods, rightGoods, priceOrder));
        }
    }

    private static int buildStableSeed(Map<String, Object> sourceMap) {
        String sourceKey =
                readStringValue(sourceMap, "sku")
                        + "|"
                        + readStringValue(sourceMap, "itemUrl")
                        + "|"
                        + readStringValue(sourceMap, "title")
                        + "|"
                        + readStringValue(sourceMap, "price");
        return sourceKey.hashCode() & Integer.MAX_VALUE;
    }

    private static List<String> buildFeatureTags(
            int stableSeed, int salesVolume, int popularityScore, int listedDays) {
        List<String> featureTags = new ArrayList<String>();
        if (stableSeed % 2 == 0) {
            featureTags.add("自营");
        }
        if (stableSeed % 3 == 0) {
            featureTags.add("次日达");
        }
        if (listedDays <= 7) {
            featureTags.add("新品");
        }
        if (salesVolume >= 7000 && featureTags.size() < 3) {
            featureTags.add("热销");
        }
        if (popularityScore >= 95 && featureTags.size() < 3) {
            featureTags.add("高人气");
        }
        return featureTags;
    }

    private static int readIntValue(Map<String, Object> sourceMap, String fieldName) {
        Object fieldValue = sourceMap.get(fieldName);
        if (fieldValue == null) {
            return 0;
        }

        if (fieldValue instanceof Number) {
            return ((Number) fieldValue).intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(fieldValue));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String readStringValue(Map<String, Object> sourceMap, String fieldName) {
        Object fieldValue = sourceMap.get(fieldName);
        return fieldValue == null ? "" : String.valueOf(fieldValue);
    }

    private static double parsePrice(String priceText) {
        String normalizedPrice = priceText == null ? "" : priceText.replaceAll("[^0-9.]", "");
        if (normalizedPrice.isEmpty()) {
            return -1D;
        }

        try {
            return Double.parseDouble(normalizedPrice);
        } catch (NumberFormatException exception) {
            return -1D;
        }
    }

    private static int comparePrice(
            Map<String, Object> leftGoods, Map<String, Object> rightGoods, String priceOrder) {
        double leftPrice = parsePrice(readStringValue(leftGoods, "price"));
        double rightPrice = parsePrice(readStringValue(rightGoods, "price"));

        if (leftPrice < 0 && rightPrice < 0) {
            return 0;
        }
        if (leftPrice < 0) {
            return 1;
        }
        if (rightPrice < 0) {
            return -1;
        }

        int compareResult = Double.compare(leftPrice, rightPrice);
        return ORDER_DESC.equalsIgnoreCase(priceOrder) ? -compareResult : compareResult;
    }

    private static String formatCount(int count) {
        if (count >= 10000) {
            return COUNT_FORMAT.format(count / 10000.0D) + "万+";
        }
        return count + "+";
    }
}
