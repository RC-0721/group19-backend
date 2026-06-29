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

class PracticeServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final PracticeService practiceService = new PracticeService(jdbcTemplate);

    @Test
    void submitCreatesCorrectRecord() {
        when(jdbcTemplate.queryForList(anyString(), eq("q-1"))).thenReturn(List.of(Map.of(
                "answer", "A",
                "answer_analysis", "解析",
                "audit_status", "已发布"
        )));

        Map<String, Object> result = practiceService.submit("q-1", "A", "daily", "job-java", user());

        assertEquals(true, result.get("is_correct"));
        assertEquals(100, result.get("score"));
        assertEquals("无需记录", result.get("wrong_book_status"));
    }

    @Test
    void submitWrongAnswerWritesWrongBook() {
        when(jdbcTemplate.queryForList(anyString(), eq("q-1"))).thenReturn(List.of(Map.of(
                "answer", "A",
                "answer_analysis", "解析",
                "audit_status", "已发布"
        )));

        Map<String, Object> result = practiceService.submit("q-1", "B", "daily", "job-java", user());

        assertEquals(false, result.get("is_correct"));
        assertEquals(0, result.get("score"));
        assertEquals("未掌握", result.get("wrong_book_status"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("student001"), eq("q-1"), eq("未掌握"));
    }

    @Test
    void submitRejectsMissingQuestion() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> practiceService.submit("missing", "A", "daily", "job-java", user()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void submitRejectsUnpublishedQuestion() {
        when(jdbcTemplate.queryForList(anyString(), eq("q-1"))).thenReturn(List.of(Map.of(
                "answer", "A",
                "answer_analysis", "解析",
                "audit_status", "待审核"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> practiceService.submit("q-1", "A", "daily", "job-java", user()));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    private static User user() {
        User user = new User();
        user.setAccount("student001");
        user.setRole("STUDENT");
        return user;
    }
}
