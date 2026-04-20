package com.example.demo.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaRankingUtilTest {

    @Test
    void shouldPreferHigherBm25ScoreWhenQuestionRecallIsSame() {
        CourseQaRankingUtil.ScoreBreakdown lowerBm25Score =
                CourseQaRankingUtil.scoreCandidate(0.90D, 2D, 8D);
        CourseQaRankingUtil.ScoreBreakdown higherBm25Score =
                CourseQaRankingUtil.scoreCandidate(0.90D, 8D, 8D);

        Assertions.assertTrue(higherBm25Score.getAnswerRerankScore()
                > lowerBm25Score.getAnswerRerankScore());
        Assertions.assertTrue(higherBm25Score.getTotalScore() > lowerBm25Score.getTotalScore());
    }

    @Test
    void shouldBlendQuestionRecallAndNormalizedBm25ByConfiguredWeights() {
        CourseQaRankingUtil.ScoreBreakdown scoreBreakdown =
                CourseQaRankingUtil.scoreCandidate(0.75D, 3D, 6D);

        Assertions.assertEquals(0.50D, scoreBreakdown.getAnswerRerankScore(), 1e-9);
        Assertions.assertEquals(
                0.75D * CourseQaRankingUtil.TOTAL_QUESTION_RECALL_WEIGHT
                        + 0.50D * CourseQaRankingUtil.TOTAL_ANSWER_RERANK_WEIGHT,
                scoreBreakdown.getTotalScore(),
                1e-9);
    }

    @Test
    void shouldReturnZeroBm25ScoreWhenRawScoreOrBaseIsInvalid() {
        CourseQaRankingUtil.ScoreBreakdown zeroRawScore =
                CourseQaRankingUtil.scoreCandidate(0.80D, 0D, 5D);
        CourseQaRankingUtil.ScoreBreakdown zeroBaseScore =
                CourseQaRankingUtil.scoreCandidate(0.80D, 5D, 0D);

        Assertions.assertEquals(0D, zeroRawScore.getAnswerRerankScore(), 1e-9);
        Assertions.assertEquals(0D, zeroBaseScore.getAnswerRerankScore(), 1e-9);
        Assertions.assertEquals(
                0.80D * CourseQaRankingUtil.TOTAL_QUESTION_RECALL_WEIGHT,
                zeroRawScore.getTotalScore(),
                1e-9);
        Assertions.assertEquals(
                0.80D * CourseQaRankingUtil.TOTAL_QUESTION_RECALL_WEIGHT,
                zeroBaseScore.getTotalScore(),
                1e-9);
    }
}
