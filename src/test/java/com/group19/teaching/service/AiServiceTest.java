package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);

    @Test
    void mockChatReturnsContentAndWritesSuccessLog() {
        AiService service = new AiService(jdbcTemplate, List.of(new MockAiProvider("mock-ai")), "mock", "mock-ai");

        Map<String, Object> result = service.chat(Map.of("scene", "CHAT", "prompt", "解释 Spring"), user());

        assertEquals("mock-ai", result.get("model"));
        verify(jdbcTemplate).update(contains("INSERT INTO ai_call_log"),
                any(), any(), any(), any(), any(), any(), eq("成功"), any(), any(), any(), any(), any());
    }

    @Test
    void unavailableProviderWritesFailureLogAndReturns50201() {
        AiProvider failing = new AiProvider() {
            @Override
            public boolean supports(String provider) {
                return "deepseek".equals(provider);
            }

            @Override
            public AiProviderResult chat(AiRequest request) {
                throw new IllegalStateException("AI API key is missing");
            }
        };
        AiService service = new AiService(jdbcTemplate, List.of(failing), "deepseek", "deepseek-v4-flash");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.chat(Map.of("scene", "CHAT", "prompt", "解释 Spring"), user()));

        assertEquals(ErrorCode.AI_UNAVAILABLE, exception.errorCode());
        verify(jdbcTemplate).update(contains("INSERT INTO ai_call_log"),
                any(), any(), any(), any(), any(), any(), eq("失败"), any(), any(), any(), any(), any());
    }

    @Test
    void streamRejectsMissingPrompt() {
        AiService service = new AiService(jdbcTemplate, List.of(new MockAiProvider("mock-ai")), "mock", "mock-ai");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.stream(Map.of("scene", "CHAT"), user()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    private static User user() {
        User user = new User();
        user.setAccount("teacher001");
        user.setRole("TEACHER");
        return user;
    }
}
