package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.pojo.courseqa.CourseQaMetricsClearResponse;
import com.example.demo.pojo.courseqa.CourseQaMetricsUpdateResponse;
import com.example.demo.service.CourseQaSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseQaController.class)
class CourseQaControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CourseQaSearchService courseQaSearchService;

    @Test
    void updateQaMetricsShouldAcceptJsonPayload() throws Exception {
        given(courseQaSearchService.updateAnswerMetrics(any()))
                .willReturn(new CourseQaMetricsUpdateResponse("question-1", 2));

        mockMvc.perform(
                        post("/updateQAMetrics")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\n"
                                                + "  \"questionDocumentId\": \"question-1\",\n"
                                                + "  \"answers\": [\n"
                                                + "    {\"answerDocumentId\": \"answer-1\", \"likeCount\": 2, \"clickCount\": 3},\n"
                                                + "    {\"answerDocumentId\": \"answer-2\", \"likeCount\": 1, \"clickCount\": 4}\n"
                                                + "  ]\n"
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionDocumentId").value("question-1"))
                .andExpect(jsonPath("$.updatedAnswerCount").value(2));
    }

    @Test
    void clearQaShouldReturnClearedCount() throws Exception {
        given(courseQaSearchService.clearQaMetrics()).willReturn(new CourseQaMetricsClearResponse(10));

        mockMvc.perform(post("/clearQA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearedAnswerCount").value(10));
    }
}
