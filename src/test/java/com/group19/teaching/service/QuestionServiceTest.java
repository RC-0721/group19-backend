package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
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
}
