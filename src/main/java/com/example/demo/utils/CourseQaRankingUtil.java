package com.example.demo.utils;

import java.util.Set;

/**
 * 课程问答答案重排工具。
 * 排序只看查询、问题、答案文本本身的相关性与信息量，不读取 answer_quality 标签。
 */
public final class CourseQaRankingUtil {

    private CourseQaRankingUtil() {
        // 工具类不需要实例化。
    }

    public static ScoreBreakdown scoreCandidate(
            String keyword,
            String questionTokensText,
            String answerText,
            String answerTokensText,
            double normalizedQuestionRecallScore) {
        Set<String> keywordTokens = CourseQaTextUtil.buildSearchTokenSet(keyword);
        Set<String> answerTokens = CourseQaTextUtil.parseStoredTokens(answerTokensText);
        Set<String> questionTokens = CourseQaTextUtil.parseStoredTokens(questionTokensText);
        double questionCoverage = CourseQaTextUtil.calculateContainment(keywordTokens, questionTokens);
        double answerCoverage = CourseQaTextUtil.calculateContainment(keywordTokens, answerTokens);
        double answerLengthScore = buildAnswerLengthScore(answerText);

        double answerRerankScore = 0.70D * answerCoverage + 0.30D * answerLengthScore;
        double totalScore =
                0.60D * normalizedQuestionRecallScore
                        + 0.15D * questionCoverage
                        + 0.25D * answerRerankScore;
        return new ScoreBreakdown(
                totalScore,
                normalizedQuestionRecallScore,
                answerRerankScore,
                questionCoverage,
                answerCoverage,
                answerLengthScore);
    }

    private static double buildAnswerLengthScore(String answerText) {
        int effectiveLength = CourseQaTextUtil.effectiveLength(answerText);
        return Math.min(effectiveLength, 120) / 120D;
    }

    /**
     * 返回排序分数的拆解结果，方便接口调试和文档解释。
     */
    public static final class ScoreBreakdown {
        private final double totalScore;
        private final double questionRecallScore;
        private final double answerRerankScore;
        private final double questionCoverage;
        private final double answerCoverage;
        private final double answerLengthScore;

        public ScoreBreakdown(
                double totalScore,
                double questionRecallScore,
                double answerRerankScore,
                double questionCoverage,
                double answerCoverage,
                double answerLengthScore) {
            this.totalScore = totalScore;
            this.questionRecallScore = questionRecallScore;
            this.answerRerankScore = answerRerankScore;
            this.questionCoverage = questionCoverage;
            this.answerCoverage = answerCoverage;
            this.answerLengthScore = answerLengthScore;
        }

        public double getTotalScore() {
            return totalScore;
        }

        public double getQuestionRecallScore() {
            return questionRecallScore;
        }

        public double getAnswerRerankScore() {
            return answerRerankScore;
        }

        public double getQuestionCoverage() {
            return questionCoverage;
        }

        public double getAnswerCoverage() {
            return answerCoverage;
        }

        public double getAnswerLengthScore() {
            return answerLengthScore;
        }
    }
}
