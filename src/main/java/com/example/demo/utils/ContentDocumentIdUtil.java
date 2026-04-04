package com.example.demo.utils;

import com.example.demo.pojo.Content;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

/**
 * 统一生成商品文档 ID。
 * 规则优先使用稳定主键，其次回退到可重复计算的内容哈希，保证重复导入时写入幂等。
 */
public final class ContentDocumentIdUtil {

    private static final String JD_SKU_PREFIX = "jd-sku-";
    private static final String JD_HASH_PREFIX = "jd-hash-";

    private ContentDocumentIdUtil() {
        // 工具类不需要实例化。
    }

    public static String buildDocumentId(Content content) {
        if (content == null) {
            return JD_HASH_PREFIX + sha256Hex("empty-content");
        }
        return buildDocumentId(
            content.getSku(), content.getTitle(), content.getImg(), content.getItemUrl(), content.getPrice());
    }

    public static String buildDocumentId(Map<String, Object> contentSource) {
        if (contentSource == null) {
            return JD_HASH_PREFIX + sha256Hex("empty-source");
        }
        return buildDocumentId(
            toStringValue(contentSource.get("sku")),
            toStringValue(contentSource.get("title")),
            toStringValue(contentSource.get("img")),
            toStringValue(contentSource.get("itemUrl")),
            toStringValue(contentSource.get("price")));
    }

    private static String buildDocumentId(
            String sku, String title, String img, String itemUrl, String price) {
        String normalizedSku = normalizeText(sku);
        if (!normalizedSku.isEmpty()) {
            return JD_SKU_PREFIX + normalizedSku;
        }

        String normalizedItemUrl = normalizeUrl(itemUrl);
        if (!normalizedItemUrl.isEmpty()) {
            return JD_HASH_PREFIX + sha256Hex(normalizedItemUrl);
        }

        String normalizedTitle = normalizeText(title);
        String normalizedImg = normalizeUrl(img);
        String primaryPayload = normalizedTitle + "|" + normalizedImg;
        if (!isIdentityBlank(primaryPayload)) {
            return JD_HASH_PREFIX + sha256Hex(primaryPayload);
        }

        String fallbackPayload = normalizedTitle + "|" + normalizeText(price);
        return JD_HASH_PREFIX + sha256Hex(fallbackPayload);
    }

    private static boolean isIdentityBlank(String value) {
        return value == null || value.replace("|", "").trim().isEmpty();
    }

    private static String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeUrl(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue.startsWith("//")) {
            return "https:" + normalizedValue;
        }
        return normalizedValue;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte currentByte : digest) {
                builder.append(String.format("%02x", currentByte & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }
}
