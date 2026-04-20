package com.example.demo.pojo.courseqa;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前页面待保存答案互动指标的批量保存请求。
 * questionDocumentId 只作为触发保存时的上下文标识，真正保存的数据以 answers 为准。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaMetricsUpdateRequest {
    private String questionDocumentId;
    private List<CourseQaAnswerMetricsUpdateItem> answers =
            new ArrayList<CourseQaAnswerMetricsUpdateItem>();
}
