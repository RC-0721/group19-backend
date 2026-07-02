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

class TeachingStandardServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final TeachingStandardService teachingStandardService = new TeachingStandardService(jdbcTemplate);

    @Test
    void listForTeacherOnlyReturnsOwnCourseStandards() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM teaching_standard ts"),
                eq(Integer.class), any())).thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM teaching_standard ts"), any(Object[].class)))
                .thenReturn(List.of(Map.of("standard_id", "standard-java-backend", "status", "启用")));

        Map<String, Object> result = teachingStandardService.list(null, null, null, "启用", 1, 10,
                user(2L, "teacher001", "TEACHER"));

        assertEquals(1, result.get("total"));
        verify(jdbcTemplate).queryForObject(contains("cc.teacher_id = ?"), eq(Integer.class),
                eq("启用"), eq("teacher001"), eq("teacher001"));
        verify(jdbcTemplate).queryForList(contains("cc.teacher_id = ?"),
                eq("启用"), eq("teacher001"), eq("teacher001"), eq(10), eq(0));
    }

    @Test
    void createInsertsTeachingStandardAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(contains("FROM major"), eq(Integer.class), eq("major-cs"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM course"), eq(Integer.class), eq("course-java-001"))).thenReturn(1);

        Map<String, Object> result = teachingStandardService.create(Map.of(
                "major_id", "major-cs",
                "course_id", "course-java-001",
                "standard_type", "课程目标",
                "title", "Java 后端课程目标",
                "content", "掌握 Java 后端开发基础",
                "version", "2026",
                "status", "启用"
        ), actor);

        assertEquals("启用", result.get("status"));
        verify(jdbcTemplate).update(contains("INSERT INTO teaching_standard"),
                any(), eq("major-cs"), eq("course-java-001"), eq("课程目标"), eq("Java 后端课程目标"),
                eq("掌握 Java 后端开发基础"), eq("2026"), eq("启用"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_TEACHING_STANDARD"));
    }

    @Test
    void createRejectsMissingBinding() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> teachingStandardService.create(Map.of(
                        "standard_type", "课程目标",
                        "title", "Java 后端课程目标",
                        "content", "掌握 Java 后端开发基础",
                        "version", "2026",
                        "status", "启用"
                ), user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void createRejectsMissingCourse() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("course-missing"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> teachingStandardService.create(Map.of(
                        "course_id", "course-missing",
                        "standard_type", "课程目标",
                        "title", "Java 后端课程目标",
                        "content", "掌握 Java 后端开发基础",
                        "version", "2026",
                        "status", "启用"
                ), user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void updateChangesContentAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(contains("FROM teaching_standard"), eq(Integer.class),
                eq("standard-java-backend"))).thenReturn(1);

        Map<String, Object> result = teachingStandardService.update("standard-java-backend", Map.of(
                "title", "新版课程目标",
                "content", "补充工程实践",
                "version", "2026-v2",
                "status", "停用"
        ), actor);

        assertEquals("standard-java-backend", result.get("standard_id"));
        assertEquals("停用", result.get("status"));
        verify(jdbcTemplate).update(contains("UPDATE teaching_standard SET status = ?"),
                eq("停用"), eq("新版课程目标"), eq("补充工程实践"), eq("2026-v2"), eq("standard-java-backend"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("UPDATE_TEACHING_STANDARD"));
    }

    @Test
    void listRejectsInvalidStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> teachingStandardService.list(null, null, null, "草稿", 1, 10,
                        user(9L, "admin001", "EDU_ADMIN")));

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
