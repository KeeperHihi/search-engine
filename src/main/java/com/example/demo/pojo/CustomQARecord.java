package com.example.demo.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomQARecord {
    private String id;
    private String qid;
    private String topic;
    private String question;
    private String answer;
    private Integer answerQuality;
}
