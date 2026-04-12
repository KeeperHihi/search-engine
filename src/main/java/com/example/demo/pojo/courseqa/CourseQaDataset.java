package com.example.demo.pojo.courseqa;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程问答数据集的扁平化表示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaDataset {
    private String sourceName;
    private int categoryCount;
    private List<CourseQaQuestionItem> questions = new ArrayList<CourseQaQuestionItem>();
}
