package com.example.demo.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.example.demo.pojo.Answer;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.Question;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    private <T> List<T> parseArray(String jsonFilePath, Class<T> targetClass) throws IOException {
        // 所有 JSON 文件都按同一套 UTF-8 + 数组结构解析，避免重复实现。
        String jsonText = new String(Files.readAllBytes(Paths.get(jsonFilePath)), StandardCharsets.UTF_8);
        JSONArray jsonArray = JSON.parseArray(jsonText);
        return jsonArray.toJavaList(targetClass);
    }
}
