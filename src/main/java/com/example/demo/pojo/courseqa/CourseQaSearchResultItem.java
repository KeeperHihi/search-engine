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
    private String questionDocumentId;
    private String categoryName;
    private String questionId;
    private String questionText;
    private String answerText;
    private Integer answer_quality;
    private int questionRank;
    private double totalScore;
    private double questionRecallRawScore;
    private double questionRecallNormalizationBase;
    private double questionRecallScore;
    private double answerBm25RawScore;
    private double answerBm25NormalizationBase;
    private double answerBm25Score;
    private int likeCount;
    private int likeNormalizationBase;
    private double likeScore;
    private int clickCount;
    private int clickNormalizationBase;
    private double clickScore;
    private double questionRecallContribution;
    private double answerBm25Contribution;
    private double likeContribution;
    private double clickContribution;
    private double totalQuestionRecallWeight;
    private double totalAnswerBm25Weight;
    private double totalLikeWeight;
    private double totalClickWeight;
}
