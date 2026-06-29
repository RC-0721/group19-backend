package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WrongBookServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final WrongBookService wrongBookService = new WrongBookService(jdbcTemplate);

    @Test
    void listRejectsInvalidPageSize() {
        assertThrows(BusinessException.class, () -> wrongBookService.list("student001", 1, 0));
    }

    @Test
    void listReturnsStudentWrongBookRows() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student001"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq(10), eq(0))).thenReturn(List.of(Map.of(
                "question_id", "q-1",
                "stem", "题干",
                "wrong_count", 2,
                "master_status", "未掌握"
        )));

        Map<String, Object> result = wrongBookService.list("student001", 1, 10);

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
    }
}
