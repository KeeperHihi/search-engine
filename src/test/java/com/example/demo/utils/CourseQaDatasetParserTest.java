package com.example.demo.utils;

import com.example.demo.pojo.courseqa.CourseQaDataset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CourseQaDatasetParserTest {

    @Test
    void shouldParseSampleCourseQaDataset() throws IOException {
        CourseQaDatasetParser parser = new CourseQaDatasetParser();
        byte[] datasetBytes = Files.readAllBytes(Paths.get("data/course_qa.json"));

        CourseQaDataset dataset = parser.parse(datasetBytes, "course_qa.json");

        Assertions.assertEquals("course_qa.json", dataset.getSourceName());
        Assertions.assertEquals(4, dataset.getCategoryCount());
        Assertions.assertEquals(80, dataset.getQuestions().size());
        Assertions.assertEquals("日常生活", dataset.getQuestions().get(0).getCategoryName());
        Assertions.assertEquals("在超市购物时，如何判断水果是否新鲜？", dataset.getQuestions().get(0).getQuestionText());
        Assertions.assertEquals(10, dataset.getQuestions().get(0).getAnswers().size());
    }
}
