package com.example.demo.pojo;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomAnswer {
    private String id;
    private String answer;

    @JSONField(name = "answer_quality")
    private Integer answerQuality;
}
