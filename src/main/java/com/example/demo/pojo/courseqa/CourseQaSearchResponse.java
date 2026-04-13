package com.example.demo.pojo.courseqa;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全文搜索接口响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaSearchResponse {
    private String keyword;
    private List<String> keywordTerms = new ArrayList<String>();
    private String recallStrategy;
    private int topK;
    private double topPThreshold;
    private int pageNo;
    private int pageSize;
    private int recalledQuestionCount;
    private int recalledAnswerCount;
    private int returnedAnswerCount;
    private List<CourseQaSearchResultItem> items = new ArrayList<CourseQaSearchResultItem>();
}
