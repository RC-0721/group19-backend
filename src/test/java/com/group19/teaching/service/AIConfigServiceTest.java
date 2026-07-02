package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AIConfigServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final AIConfigService aiConfigService = new AIConfigService(jdbcTemplate);

    @Test
    void getReturnsDefaultConfig() {
        when(jdbcTemplate.queryForList(anyString(), eq("default"))).thenReturn(List.of(Map.of(
                "config_id", "default",
                "model_name", "mock-ai",
                "prompt_version", "v1",
                "timeout_ms", 30000,
                "fallback_enabled", true,
                "status", "启用"
        )));

        Map<String, Object> result = aiConfigService.get();

        assertEquals("mock-ai", result.get("model_name"));
    }

    @Test
    void updateRejectsBadTimeout() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiConfigService.update(Map.of(
                        "model_name", "mock-ai",
                        "prompt_version", "v1",
                        "timeout_ms", 1,
                        "fallback_enabled", true,
                        "status", "启用"
                ), user()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void updateWritesAuditLog() {
        when(jdbcTemplate.update(anyString(), eq("deepseek-chat"), eq("v2"), eq(30000), eq(true), eq("启用"), eq("default")))
                .thenReturn(1);

        Map<String, Object> result = aiConfigService.update(Map.of(
                "model_name", "deepseek-chat",
                "prompt_version", "v2",
                "timeout_ms", 30000,
                "fallback_enabled", true,
                "status", "启用"
        ), user());

        assertEquals("default", result.get("config_id"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("admin001"), eq("EDU_ADMIN"),
                eq("AI_CONFIG"), eq("UPDATE_AI_CONFIG"), eq("SUCCESS"));
    }

    private static User user() {
        User user = new User();
        user.setAccount("admin001");
        user.setRole("EDU_ADMIN");
        return user;
    }
}
