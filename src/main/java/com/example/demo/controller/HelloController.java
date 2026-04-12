package com.example.demo.controller;

import com.example.demo.pojo.courseqa.CourseQaAnswerDetail;
import com.example.demo.service.CourseQaSearchService;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HelloController {

    private final CourseQaSearchService courseQaSearchService;

    public HelloController(CourseQaSearchService courseQaSearchService) {
        this.courseQaSearchService = courseQaSearchService;
    }

    @GetMapping({"/", "/index"})
    public String index() {
        return "index";
    }

    @GetMapping("/jdsearch")
    public String jdSearch() {
        return "jdsearch";
    }

    @GetMapping("/contentse")
    public String contentSearch() {
        return "se";
    }

    @GetMapping("/answer/{answerId}")
    public String searchAnswer(Model model, @PathVariable("answerId") String answerId)
            throws IOException {
        CourseQaAnswerDetail answerDetail = courseQaSearchService.getAnswerDetail(answerId);
        if (answerDetail == null) {
            return "redirect:/contentse";
        }

        model.addAttribute("answerId", answerDetail.getAnswerDocumentId());
        model.addAttribute("questionId", answerDetail.getQuestionId());
        model.addAttribute("categoryName", answerDetail.getCategoryName());
        model.addAttribute("questionText", answerDetail.getQuestionText());
        model.addAttribute("answerText", answerDetail.getAnswerText());
        model.addAttribute("answer_quality", answerDetail.getAnswer_quality());
        return "answer";
    }
}
