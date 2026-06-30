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
import com.group19.teaching.service.ProjectService;
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

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private AuthService authService;

    @Test
    void createStandardReturnsId() throws Exception {
        when(authService.requireRole("admin-token", "EDU_ADMIN")).thenReturn(user("admin001", "EDU_ADMIN"));
        when(projectService.createStandard(ArgumentMatchers.anyMap())).thenReturn(Map.of("standard_id", "standard-1"));

        mockMvc.perform(post("/api/project-standards")
                        .header("token", "admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"job-java-backend\",\"evaluation_dimension\":\"质量\",\"score_level\":\"优秀\",\"evidence_requirement\":\"代码\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.standard_id").value("standard-1"));
    }

    @Test
    void createStandardRejectsNonAdmin() throws Exception {
        when(authService.requireRole("teacher-token", "EDU_ADMIN"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/project-standards")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"job-java-backend\",\"evaluation_dimension\":\"质量\",\"score_level\":\"优秀\",\"evidence_requirement\":\"代码\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    @Test
    void createRubricReturnsId() throws Exception {
        when(authService.requireRole("teacher-token", "TEACHER", "EDU_ADMIN"))
                .thenReturn(user("teacher001", "TEACHER"));
        when(projectService.createRubricDimension(ArgumentMatchers.eq("standard-1"), ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("dimension_id", "rubric-1", "standard_id", "standard-1"));

        mockMvc.perform(post("/api/project-standards/standard-1/rubric-dimensions")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dimension_name\":\"接口完整度\",\"weight\":0.5,\"level_rule\":\"按完成度评分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.dimension_id").value("rubric-1"));
    }

    @Test
    void createProjectReturnsStatus() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(projectService.createProject(ArgumentMatchers.anyMap(), ArgumentMatchers.eq(teacher)))
                .thenReturn(Map.of("project_task_id", "project-1", "status", "已发布"));

        mockMvc.perform(post("/api/projects")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"course_id\":\"course-java-001\",\"job_id\":\"job-java-backend\",\"task_goal\":\"目标\",\"tech_requirement\":\"Spring Boot\",\"deliverable\":\"代码\",\"status\":\"已发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.project_task_id").value("project-1"));
    }

    @Test
    void submitProjectReturnsStatus() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(projectService.submitProject(ArgumentMatchers.eq("project-1"), ArgumentMatchers.anyMap(), ArgumentMatchers.eq(student)))
                .thenReturn(Map.of(
                        "submission_id", "submit-1",
                        "submit_status", "待评价",
                        "submit_time", LocalDateTime.parse("2026-06-30T10:00:00")
                ));

        mockMvc.perform(post("/api/projects/project-1/submissions")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artifact_path\":\"/data/project.zip\",\"description\":\"说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submission_id").value("submit-1"));
    }

    @Test
    void listSubmissionsReturnsQueue() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(projectService.listSubmissions("project-1", "待评价", 1, 10, teacher)).thenReturn(Map.of(
                "records", List.of(Map.of(
                        "submission_id", "submit-1",
                        "evaluation_id", "eval-1",
                        "submit_status", "待评价"
                )),
                "total", 1,
                "page_no", 1,
                "page_size", 10
        ));

        mockMvc.perform(get("/api/projects/project-1/submissions")
                        .header("token", "teacher-token")
                        .param("submit_status", "待评价")
                        .param("page_no", "1")
                        .param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].evaluation_id").value("eval-1"));
    }

    @Test
    void confirmEvaluationReturnsScore() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "TEACHER")).thenReturn(teacher);
        when(projectService.confirmEvaluation(ArgumentMatchers.eq("eval-1"), ArgumentMatchers.anyMap(), ArgumentMatchers.eq(teacher)))
                .thenReturn(Map.of(
                        "evaluation_id", "eval-1",
                        "teacher_score", 88.0,
                        "confirmed_time", LocalDateTime.parse("2026-06-30T10:00:00")
                ));

        mockMvc.perform(put("/api/project-evaluations/eval-1")
                        .header("token", "teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacher_score\":88,\"teacher_comment\":\"合格\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.teacher_score").value(88.0));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
