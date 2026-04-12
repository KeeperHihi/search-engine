package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入接口返回的摘要信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaImportResult {
    private String sourceName;
    private int categoryCount;
    private int importedQuestionCount;
    private int importedAnswerCount;
    private int duplicateQuestionCount;
    private int duplicateAnswerCount;
}
