package com.example.demo.utils;

/**
 * 课程问答答案重排工具。
 * 当前排序统一使用 4 个归一化指标：
 * 1. 问题召回分
 * 2. 答案 BM25 得分
 * 3. 点赞量得分
 * 4. 点击量得分
 */
public final class CourseQaRankingUtil {

    public static final double TOTAL_QUESTION_RECALL_WEIGHT = 0.20D;
    public static final double TOTAL_ANSWER_BM25_WEIGHT = 0.20D;
    public static final double TOTAL_LIKE_WEIGHT = 0.40D;
    public static final double TOTAL_CLICK_WEIGHT = 0.20D;

    private CourseQaRankingUtil() {
        // 工具类不需要实例化。
    }

    public static ScoreBreakdown scoreCandidate(
            double questionRecallRawScore,
            double questionRecallNormalizationBase,
            double answerBm25RawScore,
            double answerBm25NormalizationBase,
            int likeCount,
            int likeNormalizationBase,
            int clickCount,
            int clickNormalizationBase) {
        double normalizedQuestionRecallScore =
                normalizeScore(questionRecallRawScore, questionRecallNormalizationBase);
        double normalizedAnswerBm25Score =
                normalizeScore(answerBm25RawScore, answerBm25NormalizationBase);
        double normalizedLikeScore = normalizeScore(likeCount, likeNormalizationBase);
        double normalizedClickScore = normalizeScore(clickCount, clickNormalizationBase);
        double totalScore =
                TOTAL_QUESTION_RECALL_WEIGHT * normalizedQuestionRecallScore
                        + TOTAL_ANSWER_BM25_WEIGHT * normalizedAnswerBm25Score
                        + TOTAL_LIKE_WEIGHT * normalizedLikeScore
                        + TOTAL_CLICK_WEIGHT * normalizedClickScore;
        return new ScoreBreakdown(
                totalScore,
                questionRecallRawScore,
                questionRecallNormalizationBase,
                normalizedQuestionRecallScore,
                answerBm25RawScore,
                answerBm25NormalizationBase,
                normalizedAnswerBm25Score,
                likeCount,
                likeNormalizationBase,
                normalizedLikeScore,
                clickCount,
                clickNormalizationBase,
                normalizedClickScore);
    }

    private static double normalizeScore(double rawScore, double normalizationBase) {
        if (rawScore <= 0D || normalizationBase <= 0D) {
            return 0D;
        }
        return Math.min(rawScore / normalizationBase, 1D);
    }

    /**
     * 返回排序分数的拆解结果，方便接口调试和前端展示。
     */
    public static final class ScoreBreakdown {
        private final double totalScore;
        private final double questionRecallRawScore;
        private final double questionRecallNormalizationBase;
        private final double questionRecallScore;
        private final double answerBm25RawScore;
        private final double answerBm25NormalizationBase;
        private final double answerBm25Score;
        private final int likeCount;
        private final int likeNormalizationBase;
        private final double likeScore;
        private final int clickCount;
        private final int clickNormalizationBase;
        private final double clickScore;

        public ScoreBreakdown(
                double totalScore,
                double questionRecallRawScore,
                double questionRecallNormalizationBase,
                double questionRecallScore,
                double answerBm25RawScore,
                double answerBm25NormalizationBase,
                double answerBm25Score,
                int likeCount,
                int likeNormalizationBase,
                double likeScore,
                int clickCount,
                int clickNormalizationBase,
                double clickScore) {
            this.totalScore = totalScore;
            this.questionRecallRawScore = questionRecallRawScore;
            this.questionRecallNormalizationBase = questionRecallNormalizationBase;
            this.questionRecallScore = questionRecallScore;
            this.answerBm25RawScore = answerBm25RawScore;
            this.answerBm25NormalizationBase = answerBm25NormalizationBase;
            this.answerBm25Score = answerBm25Score;
            this.likeCount = likeCount;
            this.likeNormalizationBase = likeNormalizationBase;
            this.likeScore = likeScore;
            this.clickCount = clickCount;
            this.clickNormalizationBase = clickNormalizationBase;
            this.clickScore = clickScore;
        }

        public double getTotalScore() {
            return totalScore;
        }

        public double getQuestionRecallRawScore() {
            return questionRecallRawScore;
        }

        public double getQuestionRecallNormalizationBase() {
            return questionRecallNormalizationBase;
        }

        public double getQuestionRecallScore() {
            return questionRecallScore;
        }

        public double getAnswerBm25RawScore() {
            return answerBm25RawScore;
        }

        public double getAnswerBm25NormalizationBase() {
            return answerBm25NormalizationBase;
        }

        public double getAnswerBm25Score() {
            return answerBm25Score;
        }

        public int getLikeCount() {
            return likeCount;
        }

        public int getLikeNormalizationBase() {
            return likeNormalizationBase;
        }

        public double getLikeScore() {
            return likeScore;
        }

        public int getClickCount() {
            return clickCount;
        }

        public int getClickNormalizationBase() {
            return clickNormalizationBase;
        }

        public double getClickScore() {
            return clickScore;
        }
    }
}
