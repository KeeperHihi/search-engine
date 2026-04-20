package com.example.demo.utils;

/**
 * 课程问答答案重排工具。
 * 第二阶段不再手写覆盖率和长度规则，而是直接使用答案 BM25 分，
 * 归一化后再和问题召回分做加权求和。
 */
public final class CourseQaRankingUtil {

    public static final double TOTAL_QUESTION_RECALL_WEIGHT = 0.40D;
    public static final double TOTAL_ANSWER_RERANK_WEIGHT = 0.60D;

    private CourseQaRankingUtil() {
        // 工具类不需要实例化。
    }

    public static ScoreBreakdown scoreCandidate(
            double normalizedQuestionRecallScore,
            double answerBm25RawScore,
            double answerBm25NormalizationBase) {
        double normalizedAnswerBm25Score =
                normalizeAnswerBm25Score(answerBm25RawScore, answerBm25NormalizationBase);
        double totalScore =
                TOTAL_QUESTION_RECALL_WEIGHT * normalizedQuestionRecallScore
                        + TOTAL_ANSWER_RERANK_WEIGHT * normalizedAnswerBm25Score;
        return new ScoreBreakdown(
                totalScore,
                normalizedQuestionRecallScore,
                normalizedAnswerBm25Score,
                answerBm25RawScore,
                answerBm25NormalizationBase);
    }

    private static double normalizeAnswerBm25Score(
            double answerBm25RawScore, double answerBm25NormalizationBase) {
        if (answerBm25RawScore <= 0D || answerBm25NormalizationBase <= 0D) {
            return 0D;
        }
        return Math.min(answerBm25RawScore / answerBm25NormalizationBase, 1D);
    }

    /**
     * 返回排序分数的拆解结果，方便接口调试和前端展示。
     */
    public static final class ScoreBreakdown {
        private final double totalScore;
        private final double questionRecallScore;
        private final double answerRerankScore;
        private final double answerBm25RawScore;
        private final double answerBm25NormalizationBase;

        public ScoreBreakdown(
                double totalScore,
                double questionRecallScore,
                double answerRerankScore,
                double answerBm25RawScore,
                double answerBm25NormalizationBase) {
            this.totalScore = totalScore;
            this.questionRecallScore = questionRecallScore;
            this.answerRerankScore = answerRerankScore;
            this.answerBm25RawScore = answerBm25RawScore;
            this.answerBm25NormalizationBase = answerBm25NormalizationBase;
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

        public double getAnswerBm25RawScore() {
            return answerBm25RawScore;
        }

        public double getAnswerBm25NormalizationBase() {
            return answerBm25NormalizationBase;
        }
    }
}
