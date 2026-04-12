package com.example.demo.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程问答检索共用的文本预处理工具。
 * 这里不依赖外部中文分词插件，而是统一使用“规范化文本 + 字符 n-gram”方案，
 * 方便老师现场导入数据时直接运行。
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

    public static String buildSearchTokensText(String text) {
        return joinTokens(buildSearchTokens(text));
    }

    public static Set<String> buildSearchTokenSet(String text) {
        return new LinkedHashSet<String>(buildSearchTokens(text));
    }

    public static Set<String> parseStoredTokens(String tokensText) {
        LinkedHashSet<String> tokens = new LinkedHashSet<String>();
        if (tokensText == null || tokensText.trim().isEmpty()) {
            return tokens;
        }

        String[] parts = tokensText.trim().split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
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

    public static double calculateJaccardSimilarity(Set<String> leftTokens, Set<String> rightTokens) {
        if (leftTokens.isEmpty() && rightTokens.isEmpty()) {
            return 0D;
        }

        int intersectionSize = 0;
        for (String leftToken : leftTokens) {
            if (rightTokens.contains(leftToken)) {
                intersectionSize++;
            }
        }

        int unionSize = leftTokens.size() + rightTokens.size() - intersectionSize;
        if (unionSize <= 0) {
            return 0D;
        }
        return intersectionSize / (double) unionSize;
    }

    public static double calculatePhraseContainment(String queryText, String targetText) {
        String normalizedQuery = normalizeText(queryText).replace(" ", "");
        String normalizedTarget = normalizeText(targetText).replace(" ", "");
        if (normalizedQuery.isEmpty() || normalizedTarget.isEmpty()) {
            return 0D;
        }

        if (normalizedTarget.contains(normalizedQuery)) {
            return 1D;
        }
        if (normalizedQuery.contains(normalizedTarget)) {
            return normalizedTarget.length() / (double) normalizedQuery.length();
        }
        return 0D;
    }

    public static int countSentences(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        String[] sentences = text.trim().split("[。！？；!?;]+");
        int count = 0;
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                count++;
            }
        }
        return count == 0 ? 1 : count;
    }

    public static double calculateLexicalDiversity(String text) {
        String normalizedText = normalizeText(text).replace(" ", "");
        if (normalizedText.isEmpty()) {
            return 0D;
        }

        Set<String> uniqueCharacters = new LinkedHashSet<String>();
        for (int index = 0; index < normalizedText.length(); index++) {
            uniqueCharacters.add(String.valueOf(normalizedText.charAt(index)));
        }
        return uniqueCharacters.size() / (double) normalizedText.length();
    }

    public static int effectiveLength(String text) {
        return normalizeText(text).replace(" ", "").length();
    }

    private static List<String> buildSearchTokens(String text) {
        String normalizedText = normalizeText(text);
        LinkedHashSet<String> tokens = new LinkedHashSet<String>();
        if (normalizedText.isEmpty()) {
            return new ArrayList<String>();
        }

        String[] segments = normalizedText.split("\\s+");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            tokens.add(segment);
            if (segment.length() == 1) {
                continue;
            }

            for (int ngramSize = 2; ngramSize <= 3; ngramSize++) {
                if (segment.length() < ngramSize) {
                    continue;
                }
                for (int index = 0; index <= segment.length() - ngramSize; index++) {
                    tokens.add(segment.substring(index, index + ngramSize));
                }
            }
        }
        return new ArrayList<String>(tokens);
    }

    private static String joinTokens(Collection<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(token);
        }
        return builder.toString();
    }
}
