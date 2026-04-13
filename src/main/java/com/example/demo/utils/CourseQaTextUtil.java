package com.example.demo.utils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程问答检索共用的文本预处理工具。
 * 中文分词本身交给 Elasticsearch 的 IK 插件完成，
 * 这里主要保留一些与分数计算相关的集合与长度工具。
 */
public final class CourseQaTextUtil {

    private static final Pattern TEXT_FRAGMENT_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]+");

    private CourseQaTextUtil() {
        // 工具类不需要实例化。
    }

    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        Matcher matcher = TEXT_FRAGMENT_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(matcher.group());
        }
        return builder.toString().trim();
    }

    public static double calculateContainment(Set<String> queryTokens, Set<String> textTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return 0D;
        }

        int hitCount = 0;
        for (String queryToken : queryTokens) {
            if (textTokens.contains(queryToken)) {
                hitCount++;
            }
        }
        return hitCount / (double) queryTokens.size();
    }

    public static int effectiveLength(String text) {
        return normalizeText(text).replace(" ", "").length();
    }

    public static Set<String> toTermSet(Collection<String> rawTerms) {
        LinkedHashSet<String> normalizedTerms = new LinkedHashSet<String>();
        if (rawTerms == null || rawTerms.isEmpty()) {
            return normalizedTerms;
        }

        for (String rawTerm : rawTerms) {
            if (rawTerm == null) {
                continue;
            }
            String normalizedTerm = rawTerm.trim().toLowerCase(Locale.ROOT);
            if (!normalizedTerm.isEmpty()) {
                normalizedTerms.add(normalizedTerm);
            }
        }
        return normalizedTerms;
    }
}
