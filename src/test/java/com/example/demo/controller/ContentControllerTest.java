package com.example.demo.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.service.ContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContentController.class)
public class ContentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ContentService contentService;

    @Test
    public void writeJdShouldSupportGetRequests() throws Exception {
        given(contentService.writeJDContent()).willReturn(true);

        mockMvc.perform(get("/writeJD")).andExpect(status().isOk()).andExpect(content().string("true"));
    }

    @Test
    public void writeJdShouldStillSupportPostRequests() throws Exception {
        given(contentService.writeJDContent()).willReturn(true);

        mockMvc.perform(post("/writeJD"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}
