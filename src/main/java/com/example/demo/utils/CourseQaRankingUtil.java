package com.example.demo.utils;

import java.util.Collection;
import java.util.Set;

/**
 * 课程问答答案重排工具。
 * 排序只看查询、问题、答案文本本身的相关性与信息量，不读取 answer_quality 标签。
 */
public final class CourseQaRankingUtil {

    public static final double TOTAL_QUESTION_RECALL_WEIGHT = 0.50D;
    public static final double TOTAL_ANSWER_RERANK_WEIGHT = 0.50D;
    public static final double ANSWER_COVERAGE_WEIGHT = 0.30D;
    public static final double ANSWER_LENGTH_WEIGHT = 0.70D;

    private CourseQaRankingUtil() {
        // 工具类不需要实例化。
    }

    public static ScoreBreakdown scoreCandidate(
            Collection<String> keywordTerms,
            Collection<String> questionTerms,
            String answerText,
            Collection<String> answerTerms,
            double normalizedQuestionRecallScore) {
        Set<String> keywordTermSet = CourseQaTextUtil.toTermSet(keywordTerms);
        Set<String> answerTermSet = CourseQaTextUtil.toTermSet(answerTerms);
        Set<String> questionTermSet = CourseQaTextUtil.toTermSet(questionTerms);

        // 计算 |queryTerms ∩ questionTerms| / |queryTerms|。
        // 这个值保留给调试和答辩解释使用，不再重复计入最终总分。
        double questionCoverage = CourseQaTextUtil.calculateContainment(keywordTermSet, questionTermSet);

        // 计算 |queryTerms ∩ answerTerms| / |queryTerms|
        double answerCoverage = CourseQaTextUtil.calculateContainment(keywordTermSet, answerTermSet);

        // 答案长度分，min(answerLength, 120) / 120
        double answerLengthScore = buildAnswerLengthScore(answerText);

        double answerRerankScore =
                ANSWER_COVERAGE_WEIGHT * answerCoverage
                        + ANSWER_LENGTH_WEIGHT * answerLengthScore;
        // 最终排序只看两段：
        // 1. 问题召回分：当前问题 _score / 本次查询第一名问题 _score
        // 2. 答案重排分：答案关键词覆盖率 + 答案长度
        double totalScore = 0
            + TOTAL_QUESTION_RECALL_WEIGHT * normalizedQuestionRecallScore
            + TOTAL_ANSWER_RERANK_WEIGHT * answerRerankScore;
        return new ScoreBreakdown(
            totalScore,
            normalizedQuestionRecallScore,
            answerRerankScore,
            questionCoverage,
            answerCoverage,
            answerLengthScore
        );
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
