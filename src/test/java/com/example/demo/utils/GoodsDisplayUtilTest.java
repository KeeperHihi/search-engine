package com.example.demo.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GoodsDisplayUtilTest {

    @Test
    void shouldGenerateStableMetricsForSameGoods() {
        Map<String, Object> firstGoods = buildGoods("sku-1", "Java 核心技术", "￥88.00");
        Map<String, Object> secondGoods = buildGoods("sku-1", "Java 核心技术", "￥88.00");

        GoodsDisplayUtil.enrichGoods(firstGoods);
        GoodsDisplayUtil.enrichGoods(secondGoods);

        Assertions.assertEquals(firstGoods.get("salesVolume"), secondGoods.get("salesVolume"));
        Assertions.assertEquals(firstGoods.get("popularityScore"), secondGoods.get("popularityScore"));
        Assertions.assertEquals(firstGoods.get("featureTags"), secondGoods.get("featureTags"));
    }

    @Test
    void shouldSortBySalesDescending() {
        List<Map<String, Object>> goodsList = new ArrayList<Map<String, Object>>();
        goodsList.add(buildGoodsWithSales("低销量", 1200));
        goodsList.add(buildGoodsWithSales("高销量", 9800));
        goodsList.add(buildGoodsWithSales("中销量", 3600));

        GoodsDisplayUtil.sortGoods(goodsList, GoodsDisplayUtil.SORT_SALES, GoodsDisplayUtil.ORDER_ASC);

        Assertions.assertEquals("高销量", goodsList.get(0).get("title"));
        Assertions.assertEquals("中销量", goodsList.get(1).get("title"));
        Assertions.assertEquals("低销量", goodsList.get(2).get("title"));
    }

    @Test
    void shouldSortByPriceDescending() {
        List<Map<String, Object>> goodsList = new ArrayList<Map<String, Object>>();
        goodsList.add(buildGoods("sku-1", "入门款", "￥19.90"));
        goodsList.add(buildGoods("sku-2", "旗舰款", "￥199.00"));
        goodsList.add(buildGoods("sku-3", "标准款", "￥59.00"));

        GoodsDisplayUtil.sortGoods(goodsList, GoodsDisplayUtil.SORT_PRICE, GoodsDisplayUtil.ORDER_DESC);

        Assertions.assertEquals("旗舰款", goodsList.get(0).get("title"));
        Assertions.assertEquals("标准款", goodsList.get(1).get("title"));
        Assertions.assertEquals("入门款", goodsList.get(2).get("title"));
    }

    private Map<String, Object> buildGoods(String sku, String title, String price) {
        Map<String, Object> goods = new HashMap<String, Object>();
        goods.put("sku", sku);
        goods.put("title", title);
        goods.put("price", price);
        goods.put("itemUrl", "https://example.com/" + sku);
        return goods;
    }

    private Map<String, Object> buildGoodsWithSales(String title, int salesVolume) {
        Map<String, Object> goods = buildGoods(title, title, "￥99.00");
        goods.put("salesVolume", salesVolume);
        return goods;
    }
}
