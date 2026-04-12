package com.example.demo.controller;

import com.example.demo.service.ContentService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HelloController {

    private final ContentService contentService;

    public HelloController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping({"/", "/index"})
    public String index() {
        // 根路径直接跳到商品搜索，避免保留无实际价值的中间页。
        return "redirect:/jdsearch";
    }

    @GetMapping("/jdsearch")
    public String jdSearch() {
        return "jdsearch";
    }

    @GetMapping("/contentse")
    public String contentSearch() {
        return "se";
    }

    @GetMapping("/answer/{qid}")
    public String searchAnswer(Model model, @PathVariable("qid") String qid) throws IOException {
        List<Map<String, Object>> answerList = contentService.searchAnswer(qid);
        if (answerList.isEmpty()) {
            return "redirect:/contentse";
        }

        Map<String, Object> firstAnswer = answerList.get(0);
        model.addAttribute("qid", firstAnswer.get("qid"));
        model.addAttribute("topic", firstAnswer.get("topic"));
        model.addAttribute("question", firstAnswer.get("question"));
        model.addAttribute("bestAnswer", firstAnswer.get("answer"));
        model.addAttribute("bestAnswerQuality", firstAnswer.get("answerQuality"));
        model.addAttribute("answers", answerList);
        return "answer";
    }
}
