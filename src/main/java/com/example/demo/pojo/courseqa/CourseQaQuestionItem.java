package com.example.demo.pojo.courseqa;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程问答中的单条问题记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaQuestionItem {
    private String categoryName;
    private String questionId;
    private String questionText;
    private List<CourseQaAnswerItem> answers = new ArrayList<CourseQaAnswerItem>();
}
