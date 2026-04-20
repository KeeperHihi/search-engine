package com.example.demo.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaRankingUtilTest {

    @Test
    void shouldPreferHigherLikeScoreWhenOtherScoresAreSame() {
        CourseQaRankingUtil.ScoreBreakdown lowerBm25Score =
                CourseQaRankingUtil.scoreCandidate(9D, 10D, 8D, 10D, 1, 10, 5, 10);
        CourseQaRankingUtil.ScoreBreakdown higherBm25Score =
                CourseQaRankingUtil.scoreCandidate(9D, 10D, 8D, 10D, 8, 10, 5, 10);

        Assertions.assertTrue(higherBm25Score.getLikeScore()
                > lowerBm25Score.getLikeScore());
        Assertions.assertTrue(higherBm25Score.getTotalScore() > lowerBm25Score.getTotalScore());
    }

    @Test
    void shouldBlendFourNormalizedScoresByConfiguredWeights() {
        CourseQaRankingUtil.ScoreBreakdown scoreBreakdown =
                CourseQaRankingUtil.scoreCandidate(6D, 8D, 3D, 6D, 4, 8, 1, 4);

        Assertions.assertEquals(0.75D, scoreBreakdown.getQuestionRecallScore(), 1e-9);
        Assertions.assertEquals(0.50D, scoreBreakdown.getAnswerBm25Score(), 1e-9);
        Assertions.assertEquals(0.50D, scoreBreakdown.getLikeScore(), 1e-9);
        Assertions.assertEquals(0.25D, scoreBreakdown.getClickScore(), 1e-9);
        Assertions.assertEquals(
                0.75D * CourseQaRankingUtil.TOTAL_QUESTION_RECALL_WEIGHT
                        + 0.50D * CourseQaRankingUtil.TOTAL_ANSWER_BM25_WEIGHT
                        + 0.50D * CourseQaRankingUtil.TOTAL_LIKE_WEIGHT
                        + 0.25D * CourseQaRankingUtil.TOTAL_CLICK_WEIGHT,
                scoreBreakdown.getTotalScore(),
                1e-9);
    }

    @Test
    void shouldReturnZeroScoreWhenRawScoreOrBaseIsInvalid() {
        CourseQaRankingUtil.ScoreBreakdown zeroRawScore =
                CourseQaRankingUtil.scoreCandidate(0D, 5D, 0D, 5D, 0, 3, 0, 2);
        CourseQaRankingUtil.ScoreBreakdown zeroBaseScore =
                CourseQaRankingUtil.scoreCandidate(5D, 0D, 5D, 0D, 3, 0, 2, 0);

        Assertions.assertEquals(0D, zeroRawScore.getQuestionRecallScore(), 1e-9);
        Assertions.assertEquals(0D, zeroRawScore.getAnswerBm25Score(), 1e-9);
        Assertions.assertEquals(0D, zeroBaseScore.getQuestionRecallScore(), 1e-9);
        Assertions.assertEquals(0D, zeroBaseScore.getAnswerBm25Score(), 1e-9);
        Assertions.assertEquals(0D, zeroBaseScore.getLikeScore(), 1e-9);
        Assertions.assertEquals(0D, zeroBaseScore.getClickScore(), 1e-9);
        Assertions.assertEquals(0D, zeroRawScore.getTotalScore(), 1e-9);
        Assertions.assertEquals(0D, zeroBaseScore.getTotalScore(), 1e-9);
    }
}
