package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全文搜索答案详情页 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaAnswerDetail {
    private String answerDocumentId;
    private String categoryName;
    private String questionId;
    private String questionText;
    private String answerText;
    private Integer answer_quality;
}
