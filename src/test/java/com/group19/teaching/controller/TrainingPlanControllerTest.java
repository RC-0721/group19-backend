package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.TrainingPlanService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrainingPlanController.class)
class TrainingPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrainingPlanService trainingPlanService;

    @MockBean
    private AuthService authService;

    @Test
    void listReturnsPlans() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(trainingPlanService.list("major-cs", "启用", null, 1, 10, teacher)).thenReturn(Map.of(
                "records", List.of(Map.of("plan_id", "training-plan-cs-2026", "status", "启用")),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/training-plans")
                        .header("token", "teacher-token")
                        .param("major_id", "major-cs")
                        .param("status", "启用")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].plan_id").value("training-plan-cs-2026"));
    }

    @Test
    void createRequiresAdminAndReturnsPlan() throws Exception {
        User admin = user("admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(trainingPlanService.create(any(), eq(admin))).thenReturn(Map.of(
                "plan_id", "training-plan-1",
                "major_id", "major-cs",
                "status", "启用"
        ));

        mockMvc.perform(post("/api/training-plans")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"major_id\":\"major-cs\",\"plan_name\":\"培养方案\",\"total_credits\":16,\"version\":\"2026\",\"status\":\"启用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan_id").value("training-plan-1"));
    }

    @Test
    void addPrerequisiteReturnsRelation() throws Exception {
        User admin = user("admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(admin);
        when(trainingPlanService.addPrerequisite(any(), eq(admin))).thenReturn(Map.of(
                "course_id", "course-test-001",
                "prerequisite_course_id", "course-java-001"
        ));

        mockMvc.perform(post("/api/course-prerequisites")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":\"course-test-001\",\"prerequisite_course_id\":\"course-java-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prerequisite_course_id").value("course-java-001"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
