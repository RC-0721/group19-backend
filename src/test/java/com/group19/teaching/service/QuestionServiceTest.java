package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class QuestionServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final QuestionService questionService = new QuestionService(jdbcTemplate);

    @Test
    void listRejectsInvalidPageSize() {
        assertThrows(BusinessException.class,
                () -> questionService.list(null, null, null, null, null, null, null, 1, 0));
    }

    @Test
    void listReturnsPagedRowsWithSourceFields() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq("source-javaguide"), eq("job-java-backend"), eq("%泛型%"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(),
                eq("source-javaguide"), eq("job-java-backend"), eq("%泛型%"), eq(10), eq(0))).thenReturn(List.of(Map.of(
                "question_id", "jg-q-001",
                "source_name", "JavaGuide",
                "source_license", "Apache-2.0"
        )));

        Map<String, Object> result = questionService.list(
                null, "source-javaguide", "job-java-backend", null, "泛型", null, null, 1, 10);

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
    }

    @Test
    void createQuestion() {
        Map<String, Object> result = questionService.create(Map.of(
                "question_type", "单选题",
                "stem", "Java 是什么？",
                "difficulty", "简单",
                "answer", "A",
                "answer_analysis", "Java 是编程语言",
                "knowledge_id", "kp-001",
                "job_id", "job-java-backend",
                "tech_id", "tech-001",
                "audit_status", "待审核",
                "options", List.of(Map.of(
                        "option_label", "A",
                        "option_content", "编程语言",
                        "is_correct", true
                ))
        ), user("teacher001", "TEACHER"));

        assertEquals("待审核", result.get("audit_status"));
    }

    @Test
    void auditQuestion() {
        when(jdbcTemplate.update(anyString(), eq("已发布"), eq("q-1"))).thenReturn(1);

        Map<String, Object> result = questionService.audit("q-1", "已发布", user("admin001", "EDU_ADMIN"));

        assertEquals("q-1", result.get("question_id"));
        assertEquals("已发布", result.get("audit_status"));
    }

    @Test
    void auditRejectsInvalidStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionService.audit("q-1", "通过", user("admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void auditRejectsMissingQuestion() {
        when(jdbcTemplate.update(anyString(), eq("已发布"), eq("missing"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionService.audit("missing", "已发布", user("admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void metadataForStudentUsesPublishedQuestionScope() {
        when(jdbcTemplate.queryForList(contains("q.question_type AS value")))
                .thenReturn(List.of(Map.of("value", "简答题")));
        when(jdbcTemplate.queryForList(contains("q.difficulty AS value")))
                .thenReturn(List.of(Map.of("value", "中等")));
        when(jdbcTemplate.queryForList(contains("JOIN question q")))
                .thenReturn(List.of(Map.of("knowledge_id", "kp-001", "knowledge_name", "Java 基础")));
        when(jdbcTemplate.queryForList(contains("FROM job_direction")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend", "job_name", "Java 后端开发工程师")));
        when(jdbcTemplate.queryForList(contains("FROM tech_stack")))
                .thenReturn(List.of(Map.of("tech_id", "tech-001", "tech_name", "Java", "job_id", "job-java-backend")));

        Map<String, Object> result = questionService.metadata(user("student001", "STUDENT"));

        assertEquals("简答题", ((Map<?, ?>) ((List<?>) result.get("question_types")).get(0)).get("value"));
        assertEquals("kp-001", ((Map<?, ?>) ((List<?>) result.get("knowledge_points")).get(0)).get("knowledge_id"));
        assertEquals("已发布", ((Map<?, ?>) ((List<?>) result.get("audit_statuses")).get(0)).get("value"));
    }

    @Test
    void metadataForTeacherLimitsKnowledgeToOwnCourses() {
        when(jdbcTemplate.queryForList(contains("q.question_type AS value")))
                .thenReturn(List.of(Map.of("value", "简答题")));
        when(jdbcTemplate.queryForList(contains("q.difficulty AS value")))
                .thenReturn(List.of(Map.of("value", "中等")));
        when(jdbcTemplate.queryForList(contains("JOIN course_class cc"), eq("teacher001")))
                .thenReturn(List.of(Map.of("knowledge_id", "kp-001", "knowledge_name", "Java 基础")));
        when(jdbcTemplate.queryForList(contains("FROM job_direction")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM tech_stack")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT DISTINCT audit_status AS value")))
                .thenReturn(List.of(Map.of("value", "待审核"), Map.of("value", "已发布")));

        Map<String, Object> result = questionService.metadata(user("teacher001", "TEACHER"));

        assertEquals(1, ((List<?>) result.get("knowledge_points")).size());
        assertEquals(2, ((List<?>) result.get("audit_statuses")).size());
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
