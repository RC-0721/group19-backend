package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PlatformServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final PlatformService platformService = new PlatformService(jdbcTemplate, "target/test-uploads");

    @Test
    void summaryReturnsCounts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(2);

        Map<String, Object> result = platformService.summary(null, null);

        assertEquals(1, result.get("user_count"));
        assertEquals(2, result.get("operation_log_count"));
    }

    @Test
    void listAiCallLogsRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> platformService.listAiCallLogs(null, null, null, null, null, null, 0, 10));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listAiCallLogsReturnsPagedRows() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("log_id", "ai-1", "scene", "QA", "call_status", "SUCCESS")));

        Map<String, Object> result = platformService.listAiCallLogs(
                null, "QA", "mock-local", "SUCCESS", null, null, 1, 10);

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
    }
}
