package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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

class MajorServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final MajorService majorService = new MajorService(jdbcTemplate);

    @Test
    void listForStudentOnlyReturnsEnabledMajors() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM major"), eq(Integer.class), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM major"), any(Object[].class)))
                .thenReturn(List.of(Map.of("major_id", "major-cs", "status", "启用")));

        Map<String, Object> result = majorService.list(null, null, 1, 10, user(1L, "student001", "STUDENT"));

        assertEquals(1, result.get("total"));
        verify(jdbcTemplate).queryForObject(contains("AND status = ?"), eq(Integer.class), eq("启用"));
        verify(jdbcTemplate).queryForList(contains("AND status = ?"), eq("启用"), eq(10), eq(0));
    }

    @Test
    void createInsertsMajorAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");

        Map<String, Object> result = majorService.create(Map.of(
                "major_name", "人工智能",
                "major_code", "AI",
                "description", "智能技术专业",
                "status", "启用"
        ), actor);

        assertEquals("人工智能", result.get("major_name"));
        assertEquals("启用", result.get("status"));
        verify(jdbcTemplate).update(contains("INSERT INTO major"),
                any(), eq("人工智能"), eq("AI"), eq(""), eq(""), eq("智能技术专业"), eq("启用"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_MAJOR"));
    }

    @Test
    void createRejectsInvalidStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> majorService.create(Map.of(
                        "major_name", "人工智能",
                        "major_code", "AI",
                        "status", "锁定"
                ), user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void updateRejectsMissingMajor() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("missing-major"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> majorService.update("missing-major", Map.of("status", "停用"),
                        user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void updateChangesStatusAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("major-cs"))).thenReturn(1);

        Map<String, Object> result = majorService.update("major-cs", Map.of(
                "major_name", "计算机科学与技术",
                "description", "更新后的说明",
                "status", "停用"
        ), actor);

        assertEquals("major-cs", result.get("major_id"));
        assertEquals("停用", result.get("status"));
        verify(jdbcTemplate).update(contains("UPDATE major SET status = ?"),
                eq("停用"), eq("计算机科学与技术"), eq("更新后的说明"), eq("major-cs"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("UPDATE_MAJOR"));
    }

    @Test
    void listRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> majorService.list(null, null, 0, 10, user(1L, "student001", "STUDENT")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    private static User user(Long id, String account, String role) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
