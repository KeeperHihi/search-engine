package com.example.demo.utils;

import com.example.demo.pojo.courseqa.CourseQaAnswerItem;
import com.example.demo.pojo.courseqa.CourseQaDataset;
import com.example.demo.pojo.courseqa.CourseQaQuestionItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.springframework.stereotype.Component;

/**
 * 负责把课程问答 JSON 文件解析为统一的内存结构。
 * 新数据文件是“类别 -> 问题列表 -> 候选答案列表”的嵌套结构，这里统一扁平化。
 */
@Component
public class CourseQaDatasetParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseQaDataset parse(byte[] datasetBytes, String sourceName) throws IOException {
        JsonNode rootNode = objectMapper.readTree(datasetBytes);
        if (rootNode == null || !rootNode.isObject()) {
            throw new IOException("课程问答数据必须是 JSON 对象，顶层应为“类别 -> 问题列表”结构。");
        }

        List<CourseQaQuestionItem> questionItems = new ArrayList<CourseQaQuestionItem>();
        int categoryCount = 0;
        Iterator<Entry<String, JsonNode>> fieldsIterator = rootNode.fields();
        while (fieldsIterator.hasNext()) {
            Entry<String, JsonNode> categoryEntry = fieldsIterator.next();
            categoryCount++;
            parseCategory(categoryEntry.getKey(), categoryEntry.getValue(), questionItems);
        }

        if (questionItems.isEmpty()) {
            throw new IOException("课程问答数据集中没有可导入的问题记录。");
        }

        CourseQaDataset courseQaDataset = new CourseQaDataset();
        courseQaDataset.setSourceName(sourceName);
        courseQaDataset.setCategoryCount(categoryCount);
        courseQaDataset.setQuestions(questionItems);
        return courseQaDataset;
    }

    private void parseCategory(
            String categoryName, JsonNode categoryNode, List<CourseQaQuestionItem> questionItems)
            throws IOException {
        if (categoryNode == null || !categoryNode.isArray()) {
            throw new IOException("类别 " + categoryName + " 对应的数据必须是问题数组。");
        }

        for (JsonNode questionNode : categoryNode) {
            String questionId = readText(questionNode, "id");
            String questionText = readText(questionNode, "question");
            if (questionText.isEmpty()) {
                throw new IOException("类别 " + categoryName + " 中存在 question 为空的问题记录。");
            }

            JsonNode answersNode = questionNode.get("answers");
            if (answersNode == null || !answersNode.isArray() || answersNode.size() == 0) {
                throw new IOException("问题 “" + questionText + "” 缺少 answers 数组。");
            }

            List<CourseQaAnswerItem> answers = new ArrayList<CourseQaAnswerItem>();
            for (JsonNode answerNode : answersNode) {
                String answerText = readText(answerNode, "answer");
                if (answerText.isEmpty()) {
                    continue;
                }

                Integer answer_quality = readInteger(answerNode, "answer_quality");
                answers.add(new CourseQaAnswerItem(answerText, answer_quality));
            }

            if (answers.isEmpty()) {
                throw new IOException("问题 “" + questionText + "” 没有可用答案文本。");
            }

            CourseQaQuestionItem questionItem = new CourseQaQuestionItem();
            questionItem.setCategoryName(categoryName);
            questionItem.setQuestionId(questionId);
            questionItem.setQuestionText(questionText);
            questionItem.setAnswers(answers);
            questionItems.add(questionItem);
        }
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode fieldNode = node == null ? null : node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return "";
        }
        return fieldNode.asText("").trim();
    }

    private Integer readInteger(JsonNode node, String fieldName) {
        JsonNode fieldNode = node == null ? null : node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isInt() || fieldNode.isLong()) {
            return fieldNode.asInt();
        }
        String textValue = fieldNode.asText("").trim();
        if (textValue.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(textValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
