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

class JobServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final JobService jobService = new JobService(jdbcTemplate);

    @Test
    void listRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> jobService.list(null, null, 0, 10));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listReturnsPagedStandards() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("job-java-backend"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of("skill_id", "skill-1")));

        Map<String, Object> result = jobService.list("job-java-backend", null, 1, 10);

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
    }

    @Test
    void saveCreatesMissingRows() {
        when(jdbcTemplate.queryForList(anyString(), eq("Java 后端"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("Java"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq("基础"))).thenReturn(List.of());

        Map<String, Object> result = jobService.save(Map.of(
                "job_name", "Java 后端",
                "job_description", "说明",
                "difficulty_level", "初级",
                "tech_name", "Java",
                "skill_name", "基础",
                "ability_level", "基础",
                "evidence_requirement", "证据"
        ));

        assertEquals(true, String.valueOf(result.get("job_id")).startsWith("job-"));
        assertEquals(true, String.valueOf(result.get("skill_id")).startsWith("skill-"));
    }
}
