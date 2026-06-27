package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionController.class)
class QuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsPagedQuestionData() throws Exception {
        mockMvc.perform(get("/api/questions")
                        .param("page_no", "1")
                        .param("page_size", "10")
                        .param("job_id", "job-java-backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].question_id").value("question-001"))
                .andExpect(jsonPath("$.data.records[0].job_id").value("job-java-backend"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page_no").value(1))
                .andExpect(jsonPath("$.data.page_size").value(10));
    }
}
