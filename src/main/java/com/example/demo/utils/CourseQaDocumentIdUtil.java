package com.example.demo.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 统一维护课程问答索引的稳定文档 ID。
 * 这样既能避免重复导入产生脏数据，也能让问题与答案的主键规则清晰可追踪。
 */
public final class CourseQaDocumentIdUtil {

    private static final String QUESTION_PREFIX = "course-question-";
    private static final String ANSWER_PREFIX = "course-answer-";

    private CourseQaDocumentIdUtil() {
        // 工具类不需要实例化。
    }

    public static String buildQuestionDocumentId(
            String categoryName, String questionId, String questionText) {
        String normalizedQuestionId = normalizeText(questionId);
        String identity =
                normalizeText(categoryName)
                        + "|"
                        + (normalizedQuestionId.isEmpty()
                                ? normalizeText(questionText)
                                : normalizedQuestionId);
        return QUESTION_PREFIX + sha256Hex(identity);
    }

    public static String buildAnswerDocumentId(String questionDocumentId, String answerText) {
        String identity = normalizeText(questionDocumentId) + "|" + normalizeText(answerText);
        return ANSWER_PREFIX + sha256Hex(identity);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
