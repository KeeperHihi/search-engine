package com.example.demo.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.example.demo.pojo.CustomAnswer;
import com.example.demo.pojo.CustomQA;
import com.example.demo.pojo.CustomQARecord;
import com.example.demo.pojo.CustomQuestion;
import com.example.demo.pojo.Answer;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.Question;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonParseUtil {

    public List<Question> parseJson(String jsonFilePath) throws IOException {
        return parseArray(jsonFilePath, Question.class);
    }

    public List<Answer> parseAnJson(String jsonFilePath) throws IOException {
        return parseArray(jsonFilePath, Answer.class);
    }

    public List<Content> parseJDJson(String jsonFilePath) throws IOException {
        List<Content> contents = parseArray(jsonFilePath, Content.class);
        for (Content content : contents) {
            if (content != null) {
                // 样例 JSON 里存在把多个金额写进一个 price 字段的历史数据，这里统一清洗。
                content.setPrice(PriceTextUtil.normalizeDisplayPrice(content.getPrice()));
            }
        }
        return contents;
    }

    public List<CustomQARecord> parseCustomQaJson(String jsonFilePath) throws IOException {
        String jsonText = new String(Files.readAllBytes(Paths.get(jsonFilePath)), StandardCharsets.UTF_8);
        CustomQA customQA = JSON.parseObject(jsonText, CustomQA.class);
        if (customQA == null || customQA.getQuestions() == null || customQA.getQuestions().isEmpty()) {
            return new ArrayList<CustomQARecord>();
        }

        List<CustomQARecord> records = new ArrayList<CustomQARecord>();
        String topic = safeText(customQA.getTopic());
        String normalizedTopic = normalizeIdSegment(topic);
        if (normalizedTopic.isEmpty()) {
            normalizedTopic = "custom-qa";
        }

        for (int questionIndex = 0; questionIndex < customQA.getQuestions().size(); questionIndex++) {
            CustomQuestion question = customQA.getQuestions().get(questionIndex);
            if (question == null || question.getAnswers() == null || question.getAnswers().isEmpty()) {
                continue;
            }

            String qid = buildQuestionId(normalizedTopic, question.getId(), questionIndex);
            for (int answerIndex = 0; answerIndex < question.getAnswers().size(); answerIndex++) {
                CustomAnswer answer = question.getAnswers().get(answerIndex);
                if (answer == null) {
                    continue;
                }

                CustomQARecord record = new CustomQARecord();
                record.setId(buildAnswerId(qid, answer.getId(), answerIndex));
                record.setQid(qid);
                record.setTopic(topic);
                record.setQuestion(safeText(question.getQuestion()));
                record.setAnswer(safeText(answer.getAnswer()));
                record.setAnswerQuality(normalizeAnswerQuality(answer.getAnswerQuality()));
                records.add(record);
            }
        }
        return records;
    }

    private <T> List<T> parseArray(String jsonFilePath, Class<T> targetClass) throws IOException {
        // 所有 JSON 文件都按同一套 UTF-8 + 数组结构解析，避免重复实现。
        String jsonText = new String(Files.readAllBytes(Paths.get(jsonFilePath)), StandardCharsets.UTF_8);
        JSONArray jsonArray = JSON.parseArray(jsonText);
        return jsonArray.toJavaList(targetClass);
    }

    private String buildQuestionId(String topic, String questionId, int questionIndex) {
        String normalizedQuestionId = normalizeIdSegment(questionId);
        if (!normalizedQuestionId.isEmpty()) {
            return topic + "-" + normalizedQuestionId;
        }
        return topic + "-q-" + (questionIndex + 1);
    }

    private String buildAnswerId(String qid, String answerId, int answerIndex) {
        String normalizedAnswerId = normalizeIdSegment(answerId);
        if (!normalizedAnswerId.isEmpty()) {
            return qid + "-" + normalizedAnswerId;
        }
        return qid + "-a-" + (answerIndex + 1);
    }

    private String normalizeIdSegment(String value) {
        String normalized = safeText(value).trim();
        if (normalized.isEmpty()) {
            return "";
        }
        normalized = normalized.replaceAll("[\\s]+", "-");
        normalized = normalized.replaceAll("[^\\p{L}\\p{Nd}-]+", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-", "");
        normalized = normalized.replaceAll("-$", "");
        return normalized.toLowerCase();
    }

    private Integer normalizeAnswerQuality(Integer answerQuality) {
        return answerQuality == null ? 0 : answerQuality;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
