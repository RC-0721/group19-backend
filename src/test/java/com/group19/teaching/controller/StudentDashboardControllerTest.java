package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.StudentDashboardService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentDashboardController.class)
class StudentDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private StudentDashboardService studentDashboardService;

    @Test
    void dashboardReturnsStudentAggregatedData() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("demo-token", "STUDENT")).thenReturn(student);
        when(studentDashboardService.load(student)).thenReturn(Map.of(
                "courses", List.of(Map.of("course_id", "course-java-001")),
                "todo_tasks", List.of(Map.of("task_id", "pre-java-001")),
                "practice_summary", Map.of("accuracy", 100),
                "project_summary", Map.of("submitted_count", 1),
                "interview_summary", Map.of("latest_score", 82),
                "profile_summary", Map.of("target_job", "Java 后端开发工程师")
        ));

        mockMvc.perform(get("/api/student/dashboard").header("token", "demo-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courses[0].course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.todo_tasks[0].task_id").value("pre-java-001"))
                .andExpect(jsonPath("$.data.practice_summary.accuracy").value(100))
                .andExpect(jsonPath("$.data.profile_summary.target_job").value("Java 后端开发工程师"));
    }

    @Test
    void dashboardRejectsMissingToken() throws Exception {
        when(authService.requireRole(null, "STUDENT")).thenThrow(new BusinessException(ErrorCode.AUTH_FAILED));

        mockMvc.perform(get("/api/student/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    @Test
    void dashboardRejectsNonStudent() throws Exception {
        when(authService.requireRole("teacher-token", "STUDENT")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/student/dashboard").header("token", "teacher-token"))
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
