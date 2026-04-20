package com.example.demo.pojo.courseqa;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某个问题下答案互动指标的批量保存请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaMetricsUpdateRequest {
    private String questionDocumentId;
    private List<CourseQaAnswerMetricsUpdateItem> answers =
            new ArrayList<CourseQaAnswerMetricsUpdateItem>();
}
