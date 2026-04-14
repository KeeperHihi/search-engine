package com.example.demo.utils;

import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaLogicQueryUtilTest {

    @Test
    void shouldParsePlainRequiredAndExcludedTerms() {
        CourseQaLogicQueryUtil.ParsedQuery parsedQuery =
                CourseQaLogicQueryUtil.parse("什么是神经网络 &{反向传播} !{卷积网络}");

        Assertions.assertEquals("什么是神经网络 &{反向传播} !{卷积网络}", parsedQuery.getRawKeyword());
        Assertions.assertEquals("什么是神经网络", parsedQuery.getPlainKeyword());
        Assertions.assertEquals("什么是神经网络 反向传播", parsedQuery.getRecallKeyword());
        Assertions.assertEquals(Arrays.asList("反向传播"), parsedQuery.getRequiredTerms());
        Assertions.assertEquals(Arrays.asList("卷积网络"), parsedQuery.getExcludedTerms());
    }

    @Test
    void shouldAllowRequiredTermFromAnswerText() {
        CourseQaLogicQueryUtil.ParsedQuery parsedQuery =
                CourseQaLogicQueryUtil.parse("神经网络 &{反向传播}");

        Assertions.assertTrue(
                CourseQaLogicQueryUtil.matchesAnswerCandidate(
                        "什么是神经网络？",
                        "反向传播是训练神经网络时常用的梯度计算方法。",
                        parsedQuery));
    }

    @Test
    void shouldRejectCandidateWhenExcludedTermAppearsInAnswer() {
        CourseQaLogicQueryUtil.ParsedQuery parsedQuery =
                CourseQaLogicQueryUtil.parse("神经网络 !{卷积}");

        Assertions.assertFalse(
                CourseQaLogicQueryUtil.matchesAnswerCandidate(
                        "什么是神经网络？",
                        "卷积神经网络是神经网络的一种重要结构。",
                        parsedQuery));
    }

    @Test
    void shouldRejectQuestionDuringRecallWhenExcludedTermAppearsInQuestion() {
        CourseQaLogicQueryUtil.ParsedQuery parsedQuery =
                CourseQaLogicQueryUtil.parse("神经网络 !{卷积}");

        Assertions.assertFalse(
                CourseQaLogicQueryUtil.allowsQuestion("什么是卷积神经网络？", parsedQuery));
        Assertions.assertTrue(
                CourseQaLogicQueryUtil.allowsQuestion("什么是前馈神经网络？", parsedQuery));
    }
}
