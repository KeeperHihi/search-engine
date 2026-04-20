package com.example.demo.controller;

import com.example.demo.pojo.courseqa.CourseQaImportResult;
import com.example.demo.pojo.courseqa.CourseQaMetricsClearResponse;
import com.example.demo.pojo.courseqa.CourseQaMetricsUpdateRequest;
import com.example.demo.pojo.courseqa.CourseQaMetricsUpdateResponse;
import com.example.demo.pojo.courseqa.CourseQaSearchResponse;
import com.example.demo.service.CourseQaSearchService;
import java.io.IOException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseQaController {

    private final CourseQaSearchService courseQaSearchService;

    public CourseQaController(CourseQaSearchService courseQaSearchService) {
        this.courseQaSearchService = courseQaSearchService;
    }

    @GetMapping("/writeQA")
    public CourseQaImportResult writeQa(
            @RequestParam(value = "filePath", required = false) String filePath)
            throws IOException {
        return courseQaSearchService.importDatasetFromFile(filePath);
    }

    @PostMapping("/queryse")
    public CourseQaSearchResponse queryQuestion(
            @RequestParam("keyword") String keyword,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize,
            @RequestParam(value = "recallStrategy", defaultValue = "top_k")
                    String recallStrategy,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "topP", defaultValue = "0.85") double topP)
            throws IOException {
        return courseQaSearchService.searchAnswers(
                keyword, pageNo, pageSize, recallStrategy, topK, topP);
    }

    @PostMapping("/updateQAMetrics")
    public CourseQaMetricsUpdateResponse updateQaMetrics(
            @RequestBody CourseQaMetricsUpdateRequest request) throws IOException {
        return courseQaSearchService.updateAnswerMetrics(request);
    }

    @PostMapping("/clearQA")
    public CourseQaMetricsClearResponse clearQa() throws IOException {
        return courseQaSearchService.clearQaMetrics();
    }
}
