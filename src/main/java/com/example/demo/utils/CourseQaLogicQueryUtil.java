package com.example.demo.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析全文搜索里的简单逻辑语法。
 * 支持：
 * 1. &{词}：候选结果里必须出现该词
 * 2. !{词}：候选结果里不允许出现该词
 *
 * 这里的“出现”统一按规范化后的问题文本 / 答案文本做包含判断，
 * 便于前后端都能清楚解释这套规则。
 */
public final class CourseQaLogicQueryUtil {

    private static final Pattern LOGIC_TERM_PATTERN = Pattern.compile("([&!])\\{([^{}]+)\\}");

    private CourseQaLogicQueryUtil() {
        // 工具类不需要实例化。
    }

    public static ParsedQuery parse(String rawKeyword) {
        String safeKeyword = rawKeyword == null ? "" : rawKeyword.trim();
        Matcher matcher = LOGIC_TERM_PATTERN.matcher(safeKeyword);

        LinkedHashSet<String> requiredTerms = new LinkedHashSet<String>();
        LinkedHashSet<String> excludedTerms = new LinkedHashSet<String>();
        List<String> recallFragments = new ArrayList<String>();

        StringBuilder plainKeywordBuilder = new StringBuilder();
        int currentIndex = 0;
        while (matcher.find()) {
            appendPlainFragment(plainKeywordBuilder, safeKeyword.substring(currentIndex, matcher.start()));
            currentIndex = matcher.end();

            String operator = matcher.group(1);
            String rawTerm = matcher.group(2) == null ? "" : matcher.group(2).trim();
            String normalizedTerm = CourseQaTextUtil.normalizeText(rawTerm);
            if (normalizedTerm.isEmpty()) {
                continue;
            }

            if ("&".equals(operator)) {
                requiredTerms.add(normalizedTerm);
                recallFragments.add(rawTerm);
                continue;
            }

            if ("!".equals(operator)) {
                excludedTerms.add(normalizedTerm);
            }
        }
        appendPlainFragment(plainKeywordBuilder, safeKeyword.substring(currentIndex));

        String plainKeyword = collapseWhitespace(plainKeywordBuilder.toString());
        if (!plainKeyword.isEmpty()) {
            recallFragments.add(0, plainKeyword);
        }

        return new ParsedQuery(
                safeKeyword,
                plainKeyword,
                collapseWhitespace(String.join(" ", recallFragments)),
                new ArrayList<String>(requiredTerms),
                new ArrayList<String>(excludedTerms));
    }

    public static boolean allowsQuestion(String questionText, ParsedQuery parsedQuery) {
        if (parsedQuery == null) {
            return true;
        }
        return !containsAnyTerm(questionText, parsedQuery.getExcludedTerms());
    }

    public static boolean matchesAnswerCandidate(
            String questionText, String answerText, ParsedQuery parsedQuery) {
        if (parsedQuery == null) {
            return true;
        }
        if (containsAnyTerm(questionText, parsedQuery.getExcludedTerms())
                || containsAnyTerm(answerText, parsedQuery.getExcludedTerms())) {
            return false;
        }
        return containsAllRequiredTerms(questionText, answerText, parsedQuery.getRequiredTerms());
    }

    private static boolean containsAllRequiredTerms(
            String questionText, String answerText, List<String> requiredTerms) {
        if (requiredTerms == null || requiredTerms.isEmpty()) {
            return true;
        }

        for (String requiredTerm : requiredTerms) {
            if (!containsTerm(questionText, requiredTerm) && !containsTerm(answerText, requiredTerm)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAnyTerm(String text, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return false;
        }
        for (String term : terms) {
            if (containsTerm(text, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTerm(String text, String normalizedTerm) {
        if (normalizedTerm == null || normalizedTerm.isEmpty()) {
            return false;
        }
        return CourseQaTextUtil.normalizeText(text).contains(normalizedTerm);
    }

    private static void appendPlainFragment(StringBuilder builder, String fragment) {
        String collapsedFragment = collapseWhitespace(fragment);
        if (collapsedFragment.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(collapsedFragment);
    }

    private static String collapseWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    public static final class ParsedQuery {
        private final String rawKeyword;
        private final String plainKeyword;
        private final String recallKeyword;
        private final List<String> requiredTerms;
        private final List<String> excludedTerms;

        private ParsedQuery(
                String rawKeyword,
                String plainKeyword,
                String recallKeyword,
                List<String> requiredTerms,
                List<String> excludedTerms) {
            this.rawKeyword = rawKeyword;
            this.plainKeyword = plainKeyword;
            this.recallKeyword = recallKeyword;
            this.requiredTerms = requiredTerms;
            this.excludedTerms = excludedTerms;
        }

        public String getRawKeyword() {
            return rawKeyword;
        }

        public String getPlainKeyword() {
            return plainKeyword;
        }

        public String getRecallKeyword() {
            return recallKeyword;
        }

        public List<String> getRequiredTerms() {
            return requiredTerms;
        }

        public List<String> getExcludedTerms() {
            return excludedTerms;
        }

        public boolean hasRecallKeyword() {
            return recallKeyword != null && !recallKeyword.isEmpty();
        }
    }
}
