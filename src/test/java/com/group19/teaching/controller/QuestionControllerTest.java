package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.service.QuestionService;
import com.group19.teaching.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@WebMvcTest(QuestionController.class)
class QuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionService questionService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsPagedQuestionData() throws Exception {
        when(questionService.list(null, "source-javaguide", "job-java-backend", null, "泛型", null, null, 1, 10)).thenReturn(Map.of(
                "records", List.of(Map.of(
                        "question_id", "jg-q-001",
                        "job_id", "job-java-backend",
                        "source_name", "JavaGuide",
                        "source_license", "Apache-2.0"
                )),
                "total", 20,
                "page_no", 1,
                "page_size", 10
        ));
        mockMvc.perform(get("/api/questions")
                        .header("token", "demo-token")
                        .param("page_no", "1")
                        .param("page_size", "10")
                        .param("source_id", "source-javaguide")
                        .param("job_id", "job-java-backend")
                        .param("keyword", "泛型"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].question_id").value("jg-q-001"))
                .andExpect(jsonPath("$.data.records[0].job_id").value("job-java-backend"))
                .andExpect(jsonPath("$.data.records[0].source_name").value("JavaGuide"))
                .andExpect(jsonPath("$.data.total").value(20))
                .andExpect(jsonPath("$.data.page_no").value(1))
                .andExpect(jsonPath("$.data.page_size").value(10));
    }
}
