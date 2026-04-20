package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条答案的互动指标更新项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaAnswerMetricsUpdateItem {
    private String answerDocumentId;
    private int likeCount;
    private int clickCount;
}
