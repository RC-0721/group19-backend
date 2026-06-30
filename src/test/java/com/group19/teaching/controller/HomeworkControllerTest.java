package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.HomeworkService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeworkController.class)
class HomeworkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeworkService homeworkService;

    @MockBean
    private AuthService authService;

    @Test
    void createReturnsHomework() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(homeworkService.create(ArgumentMatchers.anyMap(), ArgumentMatchers.eq(teacher))).thenReturn(Map.of(
                "homework_id", "hw-1",
                "status", "已发布"
        ));

        mockMvc.perform(post("/api/homeworks")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_class_id\":\"class-java-001\",\"title\":\"作业\",\"submit_requirement\":\"提交文本\",\"scoring_standard\":\"满分100\",\"deadline\":\"2099-01-01T00:00:00\",\"status\":\"已发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.homework_id").value("hw-1"));
    }

    @Test
    void submitReturnsStatus() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(homeworkService.submit(ArgumentMatchers.eq("hw-1"), ArgumentMatchers.anyMap(), ArgumentMatchers.eq(student)))
                .thenReturn(Map.of(
                        "submit_id", "submit-1",
                        "submit_status", "待批改",
                        "submit_time", LocalDateTime.parse("2026-06-30T10:00:00")
                ));

        mockMvc.perform(post("/api/homeworks/hw-1/submits")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submit_content\":\"答案\",\"attachment_path\":\"/tmp/a.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submit_id").value("submit-1"))
                .andExpect(jsonPath("$.data.submit_status").value("待批改"));
    }

    @Test
    void reviewReturnsScore() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(homeworkService.review(ArgumentMatchers.eq("review-1"), ArgumentMatchers.anyMap(), ArgumentMatchers.eq(teacher)))
                .thenReturn(Map.of(
                        "review_id", "review-1",
                        "teacher_score", 88.0,
                        "review_time", LocalDateTime.parse("2026-06-30T10:00:00")
                ));

        mockMvc.perform(put("/api/homework-reviews/review-1")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacher_score\":88,\"teacher_comment\":\"合格\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.teacher_score").value(88.0));
    }

    @Test
    void listSubmitsReturnsQueue() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(homeworkService.listSubmits("hw-1", "待批改", 1, 10, teacher)).thenReturn(Map.of(
                "records", List.of(Map.of(
                        "submit_id", "submit-1",
                        "review_id", "review-1",
                        "submit_status", "待批改"
                )),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/homeworks/hw-1/submits")
                        .header("token", "teacher-token")
                        .param("submit_status", "待批改")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].review_id").value("review-1"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void submitRejectsNonStudent() throws Exception {
        when(authService.requireRole("teacher-token", "STUDENT"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/homeworks/hw-1/submits")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submit_content\":\"答案\",\"attachment_path\":\"/tmp/a.txt\"}"))
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
