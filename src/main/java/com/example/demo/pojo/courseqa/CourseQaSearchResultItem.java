package com.example.demo.pojo.courseqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条排序后的答案召回结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseQaSearchResultItem {
    private String answerDocumentId;
    private String categoryName;
    private String questionId;
    private String questionText;
    private String answerText;
    private Integer answer_quality;
    private int questionRank;
    private double totalScore;
    private double questionRecallScore;
    private double answerRerankScore;
    private double answerCoverage;
    private double answerLengthScore;
    private double questionRecallContribution;
    private double answerRerankContribution;
    private double totalQuestionRecallWeight;
    private double totalAnswerRerankWeight;
    private double answerCoverageWeight;
    private double answerLengthWeight;
    private double questionPhraseScore;
    private double questionAndScore;
    private double questionLooseScore;
    private double questionPhraseBoost;
    private double questionAndBoost;
    private double questionLooseBoost;
    private double questionRecallMaxScore;
}
