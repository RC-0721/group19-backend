package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PreTaskServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final PreTaskService preTaskService = new PreTaskService(jdbcTemplate);

    @Test
    void createPreTask() {
        when(jdbcTemplate.queryForList(anyString(), eq("class-java-001"), eq("teacher001")))
                .thenReturn(List.of(Map.of("course_id", "course-java-001")));

        Map<String, Object> result = preTaskService.create(Map.of(
                "course_class_id", "class-java-001",
                "title", "预习",
                "material_id", "material-1",
                "deadline", "2099-01-01T00:00:00",
                "status", "已发布"
        ), user("teacher001", "TEACHER"));

        assertEquals("已发布", result.get("status"));
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), eq("class-java-001"), eq("course-java-001"),
                eq("预习"), eq("material-1"), eq(Timestamp.valueOf(LocalDateTime.parse("2099-01-01T00:00:00"))),
                eq("课前任务"), eq("已发布"));
    }

    @Test
    void createRejectsBadStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> preTaskService.create(Map.of(
                        "course_class_id", "class-java-001",
                        "title", "预习",
                        "material_id", "material-1",
                        "deadline", "2099-01-01T00:00:00",
                        "status", "结束"
                ), user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void submitPreTask() {
        when(jdbcTemplate.queryForList(anyString(), eq("pre-1"))).thenReturn(List.of(Map.of(
                "pre_task_id", "pre-1",
                "status", "已发布",
                "deadline", Timestamp.valueOf(LocalDateTime.now().plusDays(1))
        )));

        Map<String, Object> result = preTaskService.submit("pre-1", Map.of(
                "submit_content", "已读",
                "answer_list", List.of("A")
        ), user("student001", "STUDENT"));

        assertEquals(60.0, result.get("base_score"));
        assertEquals("", result.get("weak_points"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("pre-1"), eq("student001"), eq("已读"), eq(60.0), eq(""),
                any(Timestamp.class));
    }

    @Test
    void submitRejectsExpiredTask() {
        when(jdbcTemplate.queryForList(anyString(), eq("pre-1"))).thenReturn(List.of(Map.of(
                "pre_task_id", "pre-1",
                "status", "已发布",
                "deadline", Timestamp.valueOf(LocalDateTime.now().minusDays(1))
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> preTaskService.submit("pre-1", Map.of(
                        "submit_content", "已读",
                        "answer_list", List.of("A")
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void submitRejectsMissingTask() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> preTaskService.submit("missing", Map.of(
                        "submit_content", "已读",
                        "answer_list", List.of("A")
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
