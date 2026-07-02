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

class ClassServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final ClassService classService = new ClassService(jdbcTemplate);

    @Test
    void listForTeacherOnlyReturnsOwnTeachingClasses() {
        when(jdbcTemplate.queryForObject(contains("COUNT(DISTINCT c.class_id)"), eq(Integer.class), any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM `class` c"), any(Object[].class)))
                .thenReturn(List.of(Map.of("class_id", "class-cs-2026", "status", "启用")));

        Map<String, Object> result = classService.list(null, null, "启用", 1, 10,
                user(2L, "teacher001", "TEACHER"));

        assertEquals(1, result.get("total"));
        verify(jdbcTemplate).queryForObject(contains("cc.teacher_id = ?"), eq(Integer.class),
                eq("启用"), eq("teacher001"));
        verify(jdbcTemplate).queryForList(contains("cc.teacher_id = ?"), eq("启用"), eq("teacher001"),
                eq(10), eq(0));
    }

    @Test
    void createInsertsClassAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("major-cs"))).thenReturn(1);

        Map<String, Object> result = classService.create(Map.of(
                "major_id", "major-cs",
                "class_name", "智能 2026 班",
                "grade", "2026",
                "status", "启用"
        ), actor);

        assertEquals("major-cs", result.get("major_id"));
        assertEquals("智能 2026 班", result.get("class_name"));
        verify(jdbcTemplate).update(contains("INSERT INTO `class`"),
                any(), eq("major-cs"), eq("智能 2026 班"), eq("2026"), eq(""), eq("启用"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_CLASS"));
    }

    @Test
    void createRejectsMissingMajor() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("missing-major"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.create(Map.of(
                        "major_id", "missing-major",
                        "class_name", "智能 2026 班",
                        "status", "启用"
                ), user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void updateChangesStatusAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("class-cs-2026"))).thenReturn(1);

        Map<String, Object> result = classService.update("class-cs-2026", Map.of(
                "class_name", "计软 2026 班",
                "status", "停用"
        ), actor);

        assertEquals("class-cs-2026", result.get("class_id"));
        assertEquals("停用", result.get("status"));
        verify(jdbcTemplate).update(contains("UPDATE `class` SET status = ?"),
                eq("停用"), eq("计软 2026 班"), eq("class-cs-2026"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("UPDATE_CLASS"));
    }

    @Test
    void listRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> classService.list(null, null, null, 0, 10, user(2L, "teacher001", "TEACHER")));

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
