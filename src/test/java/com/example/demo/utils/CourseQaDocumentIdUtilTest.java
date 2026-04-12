package com.example.demo.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaDocumentIdUtilTest {

    @Test
    void shouldGenerateStableQuestionDocumentId() {
        String firstId =
                CourseQaDocumentIdUtil.buildQuestionDocumentId(
                        "自然语言处理课程知识问答", "1", "什么是自然语言处理？");
        String secondId =
                CourseQaDocumentIdUtil.buildQuestionDocumentId(
                        "自然语言处理课程知识问答", "1", "什么是自然语言处理？");

        Assertions.assertEquals(firstId, secondId);
    }

    @Test
    void shouldDifferentiateQuestionIdsAcrossCategories() {
        String nlpQuestionId =
                CourseQaDocumentIdUtil.buildQuestionDocumentId(
                        "自然语言处理课程知识问答", "1", "什么是自然语言处理？");
        String mlQuestionId =
                CourseQaDocumentIdUtil.buildQuestionDocumentId(
                        "机器学习核心问答", "1", "什么是机器学习？");

        Assertions.assertNotEquals(nlpQuestionId, mlQuestionId);
    }

    @Test
    void shouldGenerateStableAnswerDocumentId() {
        String questionDocumentId =
                CourseQaDocumentIdUtil.buildQuestionDocumentId(
                        "自然语言处理课程知识问答", "1", "什么是自然语言处理？");

        String firstAnswerId =
                CourseQaDocumentIdUtil.buildAnswerDocumentId(
                        questionDocumentId, "自然语言处理是人工智能中的一个方向。");
        String secondAnswerId =
                CourseQaDocumentIdUtil.buildAnswerDocumentId(
                        questionDocumentId, "自然语言处理是人工智能中的一个方向。");

        Assertions.assertEquals(firstAnswerId, secondAnswerId);
    }
}
