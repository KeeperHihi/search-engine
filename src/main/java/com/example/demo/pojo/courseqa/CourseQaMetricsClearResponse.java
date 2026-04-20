package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 清空互动指标后的结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaMetricsClearResponse {
    private int clearedAnswerCount;
}
