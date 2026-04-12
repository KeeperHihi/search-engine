package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原始数据文件中的候选答案。
 * answer_quality 只用于外部评估参考，导入索引与排序阶段都不会使用它。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaAnswerItem {
    private String answerText;
    private Integer answer_quality;
}
