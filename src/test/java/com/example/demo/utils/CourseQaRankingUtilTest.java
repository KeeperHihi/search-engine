package com.example.demo.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaRankingUtilTest {

    @Test
    void shouldPreferDetailedDefinitionAnswerWhenQuestionIsSame() {
        String keyword = "什么是自然语言处理";
        String questionText = "什么是自然语言处理？";
        String questionTokens = CourseQaTextUtil.buildSearchTokensText(questionText);

        CourseQaRankingUtil.ScoreBreakdown conciseAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keyword,
                        questionTokens,
                        "自然语言处理是处理文字的一种技术。",
                        CourseQaTextUtil.buildSearchTokensText("自然语言处理是处理文字的一种技术。"),
                        1D);
        CourseQaRankingUtil.ScoreBreakdown detailedAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keyword,
                        questionTokens,
                        "自然语言处理是研究如何让机器理解、分析和生成自然语言的交叉学科，广泛应用于检索、翻译和问答。",
                        CourseQaTextUtil.buildSearchTokensText(
                                "自然语言处理是研究如何让机器理解、分析和生成自然语言的交叉学科，广泛应用于检索、翻译和问答。"),
                        1D);

        Assertions.assertTrue(
                detailedAnswerScore.getTotalScore() > conciseAnswerScore.getTotalScore());
    }

    @Test
    void shouldPreferReasoningAnswerForWhyQuestion() {
        String keyword = "中文分词为什么重要";
        String questionText = "中文分词为什么重要？";
        String questionTokens = CourseQaTextUtil.buildSearchTokensText(questionText);

        CourseQaRankingUtil.ScoreBreakdown genericAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keyword,
                        questionTokens,
                        "中文分词很重要。",
                        CourseQaTextUtil.buildSearchTokensText("中文分词很重要。"),
                        1D);
        CourseQaRankingUtil.ScoreBreakdown reasoningAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keyword,
                        questionTokens,
                        "中文分词重要，因为中文书写时通常没有显式词边界，切分是否准确会直接影响后续检索和分类效果。",
                        CourseQaTextUtil.buildSearchTokensText(
                                "中文分词重要，因为中文书写时通常没有显式词边界，切分是否准确会直接影响后续检索和分类效果。"),
                        1D);

        Assertions.assertTrue(
                reasoningAnswerScore.getTotalScore() > genericAnswerScore.getTotalScore());
    }
}
