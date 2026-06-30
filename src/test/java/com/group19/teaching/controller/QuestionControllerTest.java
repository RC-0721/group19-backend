package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.QuestionService;
import com.group19.teaching.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

    @Test
    void listRejectsInvalidPageType() throws Exception {
        mockMvc.perform(get("/api/questions")
                        .header("token", "demo-token")
                        .param("page_no", "x")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40001"));
    }

    @Test
    void createReturnsQuestion() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(questionService.create(ArgumentMatchers.anyMap(), ArgumentMatchers.eq(teacher))).thenReturn(Map.of(
                "question_id", "q-1",
                "audit_status", "待审核"
        ));

        mockMvc.perform(post("/api/questions")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_type\":\"单选题\",\"stem\":\"Java 是什么？\",\"difficulty\":\"简单\",\"answer\":\"A\",\"answer_analysis\":\"解析\",\"knowledge_id\":\"kp-001\",\"job_id\":\"job-java-backend\",\"tech_id\":\"tech-001\",\"audit_status\":\"待审核\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.question_id").value("q-1"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/questions")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40001"));
    }

    @Test
    void createRejectsUnauthenticatedUser() throws Exception {
        when(authService.requireRole("bad-token", "TEACHER", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.AUTH_FAILED));

        mockMvc.perform(post("/api/questions")
                        .header("token", "bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    @Test
    void auditReturnsQuestionStatus() throws Exception {
        User admin = user("admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(questionService.audit("q-1", "已发布", admin)).thenReturn(Map.of(
                "question_id", "q-1",
                "audit_status", "已发布"
        ));

        mockMvc.perform(put("/api/questions/q-1/audit")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audit_status\":\"已发布\",\"audit_comment\":\"通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.audit_status").value("已发布"));
    }

    @Test
    void auditRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/questions/q-1/audit")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audit_status\":\"已发布\",\"audit_comment\":\"通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
