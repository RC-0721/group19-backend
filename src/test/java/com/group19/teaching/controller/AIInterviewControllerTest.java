package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AIInterviewService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AIInterviewController.class)
class AIInterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AIInterviewService interviewService;

    @MockBean
    private AuthService authService;

    @Test
    void startReturnsFirstQuestion() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.start(anyMap(), eq(student))).thenReturn(Map.of(
                "session_id", "session-1",
                "status", "已创建",
                "first_question", "问题"
        ));

        mockMvc.perform(post("/api/interviews/sessions")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"job-java-backend\",\"scene\":\"模拟面试\",\"difficulty_level\":\"初级\",\"prompt_version\":\"v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").value("session-1"));
    }

    @Test
    void sendMessageReturnsAiMessage() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.sendMessage(eq("session-1"), anyMap(), eq(student))).thenReturn(Map.of(
                "message_id", "msg-1",
                "message_content", "反馈",
                "reference_chunk", "引用",
                "status", "已完成"
        ));

        mockMvc.perform(post("/api/interviews/session-1/messages")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"回答\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已完成"));
    }

    @Test
    void reportReturnsScore() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "STUDENT", "TEACHER")).thenReturn(teacher);
        when(interviewService.report("session-1", teacher)).thenReturn(Map.of(
                "report_id", "report-1",
                "job_id", "job-java-backend",
                "score", 82.0,
                "strength", "优势",
                "weakness", "短板",
                "suggestion", "建议"
        ));

        mockMvc.perform(get("/api/interviews/sessions/session-1/report")
                        .header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(82.0));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
