package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.PreTaskService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PreTaskController.class)
class PreTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PreTaskService preTaskService;

    @MockBean
    private AuthService authService;

    @Test
    void createReturnsPreTask() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(preTaskService.create(ArgumentMatchers.anyMap(), eqUser(teacher))).thenReturn(Map.of(
                "pre_task_id", "pre-1",
                "status", "已发布"
        ));

        mockMvc.perform(post("/api/pre-tasks")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_class_id\":\"class-java-001\",\"title\":\"预习\",\"material_id\":\"material-1\",\"deadline\":\"2099-01-01T00:00:00\",\"status\":\"已发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pre_task_id").value("pre-1"));
    }

    @Test
    void submitReturnsScore() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(preTaskService.submit(ArgumentMatchers.eq("pre-1"), ArgumentMatchers.anyMap(), eqUser(student))).thenReturn(Map.of(
                "submit_id", "submit-1",
                "base_score", 60.0,
                "weak_points", "",
                "submit_time", LocalDateTime.parse("2026-06-30T10:00:00")
        ));

        mockMvc.perform(post("/api/pre-tasks/pre-1/submits")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submit_content\":\"已读\",\"answer_list\":[\"A\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submit_id").value("submit-1"))
                .andExpect(jsonPath("$.data.base_score").value(60.0));
    }

    @Test
    void submitRejectsNonStudent() throws Exception {
        when(authService.requireRole("teacher-token", "STUDENT"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/pre-tasks/pre-1/submits")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submit_content\":\"已读\",\"answer_list\":[\"A\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }

    private static User eqUser(User user) {
        return ArgumentMatchers.eq(user);
    }
}
