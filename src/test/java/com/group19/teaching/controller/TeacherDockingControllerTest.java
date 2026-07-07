package com.group19.teaching.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.TeacherDockingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherDockingController.class)
class TeacherDockingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private TeacherDockingService teacherDockingService;

    @Test
    void optionsReturnsTeacherDropdowns() throws Exception {
        User teacher = user("t2018015", "TEACHER");
        when(authService.requireUser("teacher-token")).thenReturn(teacher);
        when(teacherDockingService.options(teacher)).thenReturn(Map.of(
                "courses", List.of(Map.of("course_id", "course-java-001")),
                "course_classes", List.of(Map.of("course_class_id", "cc-java-001")),
                "chapters", List.of(),
                "materials", List.of(),
                "jobs", List.of(Map.of("job_id", "job-java-backend"))
        ));

        mockMvc.perform(get("/api/teacher/options").header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courses[0].course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.course_classes[0].course_class_id").value("cc-java-001"))
                .andExpect(jsonPath("$.data.jobs[0].job_id").value("job-java-backend"));
    }

    @Test
    void dashboardReturnsTeacherSummary() throws Exception {
        User teacher = user("t2018015", "TEACHER");
        when(authService.requireUser("teacher-token")).thenReturn(teacher);
        when(teacherDockingService.dashboard(teacher)).thenReturn(Map.ofEntries(
                Map.entry("course_count", 1),
                Map.entry("course_class_count", 1),
                Map.entry("student_count", 35),
                Map.entry("material_count", 2),
                Map.entry("question_count", 3),
                Map.entry("available_job_count", 1),
                Map.entry("project_task_count", 2),
                Map.entry("homework_count", 4),
                Map.entry("pending_homework_review_count", 1),
                Map.entry("pending_material_review_count", 0),
                Map.entry("recent_homeworks", List.of(Map.of("homework_id", "hw-1"))),
                Map.entry("recent_projects", List.of(Map.of("project_task_id", "project-1")))
        ));

        mockMvc.perform(get("/api/teacher/dashboard").header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.course_count").value(1))
                .andExpect(jsonPath("$.data.recent_homeworks[0].homework_id").value("hw-1"))
                .andExpect(jsonPath("$.data.recent_projects[0].project_task_id").value("project-1"));
    }

    @Test
    void profileAnalysisOptionsReturnsRecommendedCombination() throws Exception {
        User teacher = user("t2018015", "TEACHER");
        when(authService.requireUser("teacher-token")).thenReturn(teacher);
        when(teacherDockingService.profileAnalysisOptions(teacher)).thenReturn(Map.of(
                "items", List.of(Map.of(
                        "course_class_id", "cc-frontend-docking-b",
                        "course_id", "course-java-001",
                        "class_id", "class-frontend-docking-b",
                        "job_id", "job-java-backend",
                        "recommended", true
                ))
        ));

        mockMvc.perform(get("/api/teacher/profile-analysis-options").header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.items[0].course_id").value("course-java-001"))
                .andExpect(jsonPath("$.data.items[0].job_id").value("job-java-backend"))
                .andExpect(jsonPath("$.data.items[0].recommended").value(true));
    }

    @Test
    void profileAnalysisOptionsRejectsMissingToken() throws Exception {
        when(authService.requireUser(null)).thenThrow(new BusinessException(ErrorCode.AUTH_FAILED));

        mockMvc.perform(get("/api/teacher/profile-analysis-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    @Test
    void optionsRejectsMissingToken() throws Exception {
        when(authService.requireUser(null)).thenThrow(new BusinessException(ErrorCode.AUTH_FAILED));

        mockMvc.perform(get("/api/teacher/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40101"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
