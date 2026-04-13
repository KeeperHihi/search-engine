package com.example.demo.utils;

import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaRankingUtilTest {

    @Test
    void shouldPreferDetailedDefinitionAnswerWhenQuestionIsSame() {
        java.util.List<String> keywordTerms = Arrays.asList("自然语言处理");
        java.util.List<String> questionTerms = Arrays.asList("什么", "自然语言处理");

        CourseQaRankingUtil.ScoreBreakdown conciseAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keywordTerms,
                        questionTerms,
                        "自然语言处理是处理文字的一种技术。",
                        Arrays.asList("自然语言处理", "处理", "文字", "技术"),
                        1D);
        CourseQaRankingUtil.ScoreBreakdown detailedAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keywordTerms,
                        questionTerms,
                        "自然语言处理是研究如何让机器理解、分析和生成自然语言的交叉学科，广泛应用于检索、翻译和问答。",
                        Arrays.asList(
                                "自然语言处理",
                                "机器",
                                "理解",
                                "分析",
                                "生成",
                                "自然语言",
                                "检索",
                                "翻译",
                                "问答"),
                        1D);

        Assertions.assertTrue(
                detailedAnswerScore.getTotalScore() > conciseAnswerScore.getTotalScore());
    }

    @Test
    void shouldPreferReasoningAnswerForWhyQuestion() {
        java.util.List<String> keywordTerms = Arrays.asList("中文分词", "为什么", "重要");
        java.util.List<String> questionTerms = Arrays.asList("中文分词", "为什么", "重要");

        CourseQaRankingUtil.ScoreBreakdown genericAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keywordTerms,
                        questionTerms,
                        "中文分词很重要。",
                        Arrays.asList("中文分词", "重要"),
                        1D);
        CourseQaRankingUtil.ScoreBreakdown reasoningAnswerScore =
                CourseQaRankingUtil.scoreCandidate(
                        keywordTerms,
                        questionTerms,
                        "中文分词重要，因为中文书写时通常没有显式词边界，切分是否准确会直接影响后续检索和分类效果。",
                        Arrays.asList(
                                "中文分词",
                                "重要",
                                "因为",
                                "中文",
                                "书写",
                                "没有",
                                "词边界",
                                "切分",
                                "准确",
                                "检索",
                                "分类",
                                "效果"),
                        1D);

        Assertions.assertTrue(
                reasoningAnswerScore.getTotalScore() > genericAnswerScore.getTotalScore());
    }

    @Test
    void shouldNotCountQuestionCoverageTwiceInTotalScore() {
        java.util.List<String> keywordTerms = Arrays.asList("神经网络", "定义");
        java.util.List<String> answerTerms = Arrays.asList("神经网络", "定义", "模型");

        CourseQaRankingUtil.ScoreBreakdown highCoverageScore =
                CourseQaRankingUtil.scoreCandidate(
                        keywordTerms,
                        Arrays.asList("神经网络", "定义"),
                        "神经网络的定义。",
                        answerTerms,
                        0.8D);
        CourseQaRankingUtil.ScoreBreakdown lowCoverageScore =
                CourseQaRankingUtil.scoreCandidate(
                        keywordTerms,
                        Arrays.asList("神经网络"),
                        "神经网络的定义。",
                        answerTerms,
                        0.8D);

        Assertions.assertEquals(1.0D, highCoverageScore.getQuestionCoverage(), 1e-9);
        Assertions.assertEquals(0.5D, lowCoverageScore.getQuestionCoverage(), 1e-9);
        Assertions.assertEquals(
                highCoverageScore.getAnswerRerankScore(),
                lowCoverageScore.getAnswerRerankScore(),
                1e-9);
        Assertions.assertEquals(
                highCoverageScore.getTotalScore(),
                lowCoverageScore.getTotalScore(),
                1e-9);
    }
}
