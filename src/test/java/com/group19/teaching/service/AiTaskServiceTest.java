package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiTaskServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final AiService aiService = org.mockito.Mockito.mock(AiService.class);
    private final AiTaskService taskService = new AiTaskService(jdbcTemplate, aiService, new ObjectMapper());

    @Test
    void submitRunsTaskAndStoresResult() {
        when(aiService.chat(anyMap(), any(User.class))).thenReturn(Map.of(
                "request_id", "req-1",
                "model", "mock-ai",
                "content", "ok",
                "duration_ms", 1L
        ));

        Map<String, Object> result = taskService.submit(Map.of(
                "task_type", "MATERIAL_PARSE",
                "request", Map.of("material_id", "m1")
        ), user());

        assertEquals("已完成", result.get("task_status"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("UPDATE ai_task"),
                any(), any(), any());
    }

    @Test
    void getRejectsOtherTeacherTask() {
        when(jdbcTemplate.queryForList(anyString(), eq("task-1"))).thenReturn(List.of(Map.of(
                "task_id", "task-1",
                "task_type", "MATERIAL_PARSE",
                "created_by", "teacher002",
                "task_status", "已完成"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> taskService.get("task-1", user()));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    private static User user() {
        User user = new User();
        user.setAccount("teacher001");
        user.setRole("TEACHER");
        return user;
    }
}
