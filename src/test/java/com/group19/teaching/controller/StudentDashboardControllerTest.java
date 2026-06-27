package com.group19.teaching.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentDashboardController.class)
class StudentDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardReturnsFrontendIntegrationData() throws Exception {
        mockMvc.perform(get("/api/student/dashboard").header("token", "demo-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courses[0].course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.todo_tasks[0].task_id").value("todo-001"))
                .andExpect(jsonPath("$.data.practice_summary.accuracy").value(82))
                .andExpect(jsonPath("$.data.profile_summary.target_job").value("Java 后端开发工程师"));
    }
}
