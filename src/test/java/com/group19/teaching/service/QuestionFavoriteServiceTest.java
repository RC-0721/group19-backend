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

class QuestionFavoriteServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final QuestionFavoriteService questionFavoriteService = new QuestionFavoriteService(jdbcTemplate);

    @Test
    void listReturnsStudentFavorites() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student001"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq(10), eq(0))).thenReturn(List.of(Map.of(
                "question_id", "q-1",
                "stem", "题干",
                "question_type", "单选题",
                "difficulty", "简单"
        )));

        Map<String, Object> result = questionFavoriteService.list(1, 10, user());

        assertEquals(1, result.get("total"));
        assertEquals(1, ((List<?>) result.get("records")).size());
    }

    @Test
    void listRejectsInvalidPageSize() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionFavoriteService.list(1, 0, user()));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void addCreatesFavoriteForPublishedQuestion() {
        when(jdbcTemplate.queryForList(anyString(), eq("q-1"))).thenReturn(List.of(Map.of(
                "question_id", "q-1",
                "audit_status", "已发布"
        )));

        Map<String, Object> result = questionFavoriteService.add("q-1", user());

        assertEquals("q-1", result.get("question_id"));
        assertEquals(true, result.get("favorited"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("student001"), eq("q-1"));
    }

    @Test
    void addRejectsMissingQuestion() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionFavoriteService.add("missing", user()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void addRejectsUnpublishedQuestion() {
        when(jdbcTemplate.queryForList(anyString(), eq("q-1"))).thenReturn(List.of(Map.of(
                "question_id", "q-1",
                "audit_status", "待审核"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> questionFavoriteService.add("q-1", user()));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    @Test
    void deleteIsIdempotent() {
        Map<String, Object> result = questionFavoriteService.delete("q-1", user());

        assertEquals("q-1", result.get("question_id"));
        assertEquals(false, result.get("favorited"));
        verify(jdbcTemplate).update(anyString(), eq("student001"), eq("q-1"));
    }

    private static User user() {
        User user = new User();
        user.setAccount("student001");
        user.setRole("STUDENT");
        return user;
    }
}
