package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProjectServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final ProjectService projectService = new ProjectService(jdbcTemplate, null, new ObjectMapper());

    @Test
    void createStandard() {
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));

        Map<String, Object> result = projectService.createStandard(Map.of(
                "job_id", "job-java-backend",
                "evaluation_dimension", "项目质量",
                "score_level", "优秀/合格",
                "evidence_requirement", "提交代码和验证结果"
        ));

        verify(jdbcTemplate).queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", "job-java-backend");
        verify(jdbcTemplate).update(anyString(), anyString(), eq("job-java-backend"),
                eq("项目质量"), eq("优秀/合格"), eq("提交代码和验证结果"));
        assertEquals(true, String.valueOf(result.get("standard_id")).startsWith("standard-"));
    }

    @Test
    void createRubricDimension() {
        when(jdbcTemplate.queryForList(anyString(), eq("standard-1")))
                .thenReturn(List.of(Map.of("standard_id", "standard-1")));

        Map<String, Object> result = projectService.createRubricDimension("standard-1", Map.of(
                "dimension_name", "接口完整度",
                "weight", 0.5,
                "level_rule", "按完成度评分"
        ));

        verify(jdbcTemplate).update(anyString(), anyString(), eq("standard-1"),
                eq("接口完整度"), eq(0.5), eq("按完成度评分"));
        assertEquals("standard-1", result.get("standard_id"));
    }

    @Test
    void createRubricRejectsInvalidWeight() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.createRubricDimension("standard-1", Map.of(
                        "dimension_name", "接口完整度",
                        "weight", 2,
                        "level_rule", "按完成度评分"
                )));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void createProject() {
        when(jdbcTemplate.queryForList(anyString(), eq("course-java-001"), eq("teacher001")))
                .thenReturn(List.of(Map.of("course_id", "course-java-001")));
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));

        Map<String, Object> result = projectService.createProject(Map.of(
                "course_id", "course-java-001",
                "job_id", "job-java-backend",
                "task_goal", "完成项目",
                "tech_requirement", "Spring Boot",
                "deliverable", "代码",
                "status", "已发布"
        ), user("teacher001", "TEACHER"));

        verify(jdbcTemplate).update(anyString(), anyString(), eq("course-java-001"), eq("job-java-backend"),
                eq("完成项目"), eq("完成项目"), eq("Spring Boot"), eq("代码"),
                eq("code"), eq(null), eq("M"), eq("已发布"));
        assertEquals("已发布", result.get("status"));
    }

    @Test
    void submitProjectCreatesEvaluation() {
        when(jdbcTemplate.queryForList(anyString(), eq("project-1"))).thenReturn(List.of(Map.of(
                "project_task_id", "project-1",
                "job_id", "job-java-backend",
                "project_type", "code",
                "status", "已发布"
        )));
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("standard_id", "standard-1")));

        Map<String, Object> result = projectService.submitProject("project-1", Map.of(
                "artifact_path", "/data/project.zip",
                "description", "说明"
        ), user("student001", "STUDENT"));

        assertEquals("待评价", result.get("submit_status"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("project-1"), eq("student001"),
                eq("/data/project.zip"), eq("说明"), eq("待评价"), any(Timestamp.class));
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), eq("standard-1"), eq("待教师确认"));
    }

    @Test
    void submitRejectsDraftProject() {
        when(jdbcTemplate.queryForList(anyString(), eq("project-1"))).thenReturn(List.of(Map.of(
                "project_task_id", "project-1",
                "job_id", "job-java-backend",
                "status", "草稿"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.submitProject("project-1", Map.of(
                        "artifact_path", "/data/project.zip",
                        "description", "说明"
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void architectureProjectCreatesJsonEvaluation() {
        when(jdbcTemplate.queryForList(anyString(), eq("project-arch-1"))).thenReturn(List.of(Map.of(
                "project_task_id", "project-arch-1",
                "job_id", "job-java-backend",
                "project_type", "architecture",
                "arch_requirement", "设计课程学习系统架构",
                "arch_scale", "S",
                "status", "已发布"
        )));
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("standard_id", "standard-1")));

        Map<String, Object> result = projectService.submitProject("project-arch-1", Map.of(
                "artifact_path", "/data/arch.md",
                "description", "包含需求覆盖、安全性、数据设计和部署运维"
        ), user("student001", "STUDENT"));

        assertEquals("待评价", result.get("submit_status"));
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), eq("standard-1"),
                org.mockito.ArgumentMatchers.contains("\"dimensions\""));
    }

    @Test
    void listSubmissionsReturnsQueue() {
        when(jdbcTemplate.queryForList(anyString(), eq("project-1"), eq("teacher001")))
                .thenReturn(List.of(Map.of("project_task_id", "project-1")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("project-1"), eq("待评价")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("project-1"), eq("待评价"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of(
                        "submission_id", "submit-1",
                        "evaluation_id", "eval-1",
                        "submit_status", "待评价"
                )));

        Map<String, Object> result = projectService.listSubmissions(
                "project-1", "待评价", 1, 10, user("teacher001", "TEACHER"));

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
    }

    @Test
    void listSubmissionsRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.listSubmissions("project-1", null, 0, 10, user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listSubmissionsRejectsOtherTeacher() {
        when(jdbcTemplate.queryForList(anyString(), eq("project-1"), eq("teacher002"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.listSubmissions("project-1", null, 1, 10, user("teacher002", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void listAllSubmissionsReturnsAdminRows() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("project-1"), eq("class-1"),
                eq("cc-1"), eq("待评价"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("project-1"), eq("class-1"), eq("cc-1"),
                eq("待评价"), eq(10), eq(0))).thenReturn(List.of(Map.of(
                        "submission_id", "submit-1",
                        "evaluation_id", "eval-1",
                        "student_id", "student001",
                        "class_id", "class-1",
                        "course_class_id", "cc-1",
                        "review_status", "待评价"
                )));

        Map<String, Object> result = projectService.listAllSubmissions(
                "project-1", "class-1", "cc-1", "待评价", 1, 10, user("admin001", "EDU_ADMIN"));

        assertEquals(1, result.get("total"));
        Map<?, ?> record = (Map<?, ?>) ((List<?>) result.get("records")).get(0);
        assertEquals("submit-1", record.get("submission_id"));
        assertEquals("eval-1", record.get("evaluation_id"));
        assertEquals("待评价", record.get("review_status"));
    }

    @Test
    void listAllSubmissionsScopesTeacherRows() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("teacher001"), eq("待评价")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("teacher001"), eq("待评价"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of(
                        "submission_id", "submit-1",
                        "teacher_id", "teacher001",
                        "submit_status", "待评价"
                )));

        Map<String, Object> result = projectService.listAllSubmissions(
                null, null, null, "待评价", 1, 10, user("teacher001", "TEACHER"));

        assertEquals(1, result.get("total"));
    }

    @Test
    void confirmEvaluationWritesAbilityEvidence() {
        when(jdbcTemplate.queryForList(anyString(), eq("eval-1"), eq("teacher001"))).thenReturn(List.of(Map.of(
                "evaluation_id", "eval-1",
                "submission_id", "submit-1",
                "student_id", "student001",
                "project_task_id", "project-1"
        )));

        Map<String, Object> result = projectService.confirmEvaluation("eval-1", Map.of(
                "teacher_score", 88,
                "teacher_comment", "合格"
        ), user("teacher001", "TEACHER"));

        assertEquals(88.0, result.get("teacher_score"));
        verify(jdbcTemplate).update(anyString(), eq(88.0), eq("合格"), any(Timestamp.class), eq("eval-1"));
        verify(jdbcTemplate).update(anyString(), eq("已反馈"), eq("submit-1"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("student001"), eq("PROJECT"), eq("eval-1"), eq(88.0));
    }

    @Test
    void confirmEvaluationRejectsRepeatedConfirm() {
        when(jdbcTemplate.queryForList(anyString(), eq("eval-1"), eq("teacher001"))).thenReturn(List.of(Map.of(
                "evaluation_id", "eval-1",
                "teacher_score", 80.0,
                "submission_id", "submit-1",
                "student_id", "student001",
                "project_task_id", "project-1"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.confirmEvaluation("eval-1", Map.of(
                        "teacher_score", 88,
                        "teacher_comment", "合格"
                ), user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void listReturnsAdminProjects() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("job-java-backend"), eq("已发布")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend"), eq("已发布"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of("project_task_id", "project-1")));

        Map<String, Object> result = projectService.list(
                null, null, "job-java-backend", "已发布", 1, 10, user("admin001", "EDU_ADMIN"));

        assertEquals(1, result.get("total"));
    }

    @Test
    void listFiltersByTeacherCourseClass() {
        User teacher = user("teacher001", "TEACHER");
        when(jdbcTemplate.queryForList(anyString(), eq("cc-java-001"))).thenReturn(List.of(Map.of(
                "course_class_id", "cc-java-001",
                "course_id", "course-java-001",
                "teacher_id", "teacher001",
                "class_id", "class-1"
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("teacher001"), eq("cc-java-001")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("teacher001"), eq("cc-java-001"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of(
                        "project_task_id", "project-1",
                        "course_class_id", "cc-java-001",
                        "course_name", "Java EE",
                        "job_name", "后端开发",
                        "submit_count", 2,
                        "pending_evaluation_count", 1
                )));

        Map<String, Object> result = projectService.list(
                "cc-java-001", null, null, null, 1, 10, teacher);

        assertEquals(1, result.get("total"));
        Map<?, ?> record = (Map<?, ?>) ((List<?>) result.get("records")).get(0);
        assertEquals("cc-java-001", record.get("course_class_id"));
        assertEquals("Java EE", record.get("course_name"));
        assertEquals("后端开发", record.get("job_name"));
        assertEquals(2, record.get("submit_count"));
        assertEquals(1, record.get("pending_evaluation_count"));
    }

    @Test
    void listRejectsOtherTeacherCourseClass() {
        when(jdbcTemplate.queryForList(anyString(), eq("cc-java-001"))).thenReturn(List.of(Map.of(
                "course_class_id", "cc-java-001",
                "course_id", "course-java-001",
                "teacher_id", "teacher002",
                "class_id", "class-1"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.list("cc-java-001", null, null, null, 1, 10,
                        user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void listRejectsMismatchedCourseClassAndCourse() {
        when(jdbcTemplate.queryForList(anyString(), eq("cc-java-001"))).thenReturn(List.of(Map.of(
                "course_class_id", "cc-java-001",
                "course_id", "course-java-001",
                "teacher_id", "teacher001",
                "class_id", "class-1"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectService.list("cc-java-001", "course-python-001", null, null, 1, 10,
                        user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
