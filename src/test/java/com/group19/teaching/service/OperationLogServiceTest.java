package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

class OperationLogServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final OperationLogService operationLogService = new OperationLogService(jdbcTemplate);

    @Test
    void listReturnsPagedOperationLogs() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of(
                        "log_id", "op-1",
                        "user_id", "9",
                        "module", "USER",
                        "operation_type", "UPDATE_USER"
                )));

        Map<String, Object> result = operationLogService.list(
                "9", "USER", "UPDATE_USER", "2026-06-30 00:00:00", "2026-06-30T23:59:59", 1, 10);

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
        assertEquals(1, result.get("page_no"));
        assertEquals(10, result.get("page_size"));
    }

    @Test
    void listRejectsInvalidPageSize() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> operationLogService.list(null, null, null, null, null, 1, 101));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listRejectsInvalidTime() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> operationLogService.list(null, null, null, "bad-time", null, 1, 10));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listRejectsReversedTimeRange() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> operationLogService.list(null, null, null,
                        "2026-06-30T23:59:59", "2026-06-30T00:00:00", 1, 10));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void exportCsvEscapesCommaValues() {
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of(
                        "log_id", "op-1",
                        "user_id", "admin001",
                        "role", "EDU_ADMIN",
                        "module", "USER",
                        "operation_type", "UPDATE_USER",
                        "operation_result", "SUCCESS,OK",
                        "operation_time", "2026-07-02T10:00:00"
                )));

        String csv = operationLogService.exportCsv("admin001", null, null, null, null);

        assertTrue(csv.startsWith("log_id,user_id,role,module,operation_type,operation_result,operation_time"));
        assertTrue(csv.contains("\"SUCCESS,OK\""));
    }
}
