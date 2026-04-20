package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 互动指标保存结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaMetricsUpdateResponse {
    private String questionDocumentId;
    private int updatedAnswerCount;
}
