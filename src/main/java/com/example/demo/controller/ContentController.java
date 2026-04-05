package com.example.demo.controller;

import com.example.demo.service.ContentService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/parse/{keyword}")
    public Boolean parse(@PathVariable("keyword") String keyword) throws IOException {
        return contentService.parseContent(keyword);
    }

    @GetMapping("/writeQA")
    public Boolean writeQA() throws IOException {
        return contentService.writeQAContent();
    }

    @PostMapping("/writeJD")
    public Boolean writeJD() throws IOException {
        return contentService.writeJDContent();
    }

    @PostMapping("/query")
    public List<Map<String, Object>> query(
            @RequestParam("keyword") String keyword,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize)
            throws IOException {
        return contentService.searchPage(keyword, pageNo, pageSize);
    }

    @PostMapping("/queryse")
    public List<Map<String, Object>> queryQuestion(
            @RequestParam("keyword") String keyword,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize)
            throws IOException {
        return contentService.searchQA(keyword, pageNo, pageSize);
    }
}
