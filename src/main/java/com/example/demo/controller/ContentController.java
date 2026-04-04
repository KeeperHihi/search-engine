package com.example.demo.controller;

import com.example.demo.service.ContentService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContentController {

    @Autowired private ContentService contentService;

    //    @GetMapping("/searchAn/{aid}")
    //    public String parsese(Model model, @PathVariable("aid") String aid) throws IOException,
    // IOException {
    //        System.out.println(aid);
    //        List list=contentService.searchAnswer(aid);
    //        String str=list.toString();
    //        model.addAttribute("hello",list.toString());
    //        return "答案： \n"+ str;
    //    }

    @GetMapping("/parse/{keyword}")
    public Boolean parse(@PathVariable("keyword") String keyword) throws IOException, IOException {
        // String convStr="机器学习";
        // convStr=convStr.getBytes("UTF-8").toString();
        System.out.println(keyword);
        // return contentService.parseContent(convStr);
        return contentService.parseContent(keyword);
    }

    @GetMapping("/search/{keyword}/{pageNo}/{pageSize}")
    // @CrossOrigin(origin = {"*"}) // 如果后续拆成独立前后端，可按实际来源收紧。
    public List<Map<String, Object>> search(
            @PathVariable("keyword") String keyword,
            @PathVariable("pageNo") int pageNo,
            @PathVariable("pageSize") int pageSize)
            throws IOException {
        List<Map<String, Object>> list = contentService.searchPage(keyword, pageNo, pageSize);
        return list;
    }

    @GetMapping("/writeQA")
    public Boolean writeQA() throws IOException, IOException {
        return contentService.writeQAContent();
    }

    @PostMapping("/writeJD")
    public Boolean writeJD() throws IOException {
        // 手动把本地商品 JSON 导入到 jddata。
        return contentService.writeJDContent();
    }

    @PostMapping("/query")
    public List<Map<String, Object>> query(String keyword, int pageNo, int pageSize)
            throws IOException {
        List<Map<String, Object>> list = contentService.searchPage(keyword, pageNo, pageSize);
        return list;
    }

    @PostMapping("/queryse")
    public List<Map<String, Object>> queryse(String keyword, int pageNo, int pageSize)
            throws IOException {
        List<Map<String, Object>> list = contentService.searchQA(keyword, pageNo, pageSize);
        return list;
    }
}
