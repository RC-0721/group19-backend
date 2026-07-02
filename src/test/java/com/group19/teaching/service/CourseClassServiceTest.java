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

class CourseClassServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final CourseClassService courseClassService = new CourseClassService(jdbcTemplate);

    @Test
    void listForStudentOnlyReturnsOwnClassCourses() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM course_class cc"),
                eq(Integer.class), any())).thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM course_class cc"), any(Object[].class)))
                .thenReturn(List.of(Map.of("course_class_id", "class-java-001", "status", "开课中")));

        Map<String, Object> result = courseClassService.list(null, null, null, "开课中", 1, 10,
                user(1L, "student001", "STUDENT"));

        assertEquals(1, result.get("total"));
        verify(jdbcTemplate).queryForObject(contains("sp.student_id = ?"), eq(Integer.class),
                eq("开课中"), eq("student001"));
        verify(jdbcTemplate).queryForList(contains("sp.student_id = ?"),
                eq("开课中"), eq("student001"), eq(10), eq(0));
    }

    @Test
    void createInsertsCourseClassAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(contains("FROM course WHERE"), eq(Integer.class), eq("course-java-001")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM `class`"), eq(Integer.class), eq("class-cs-2026")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM sys_user"), eq(Integer.class), eq("teacher001")))
                .thenReturn(1);

        Map<String, Object> result = courseClassService.create(Map.of(
                "course_id", "course-java-001",
                "class_id", "class-cs-2026",
                "teacher_id", "teacher001",
                "semester", "2026 春季",
                "status", "开课中"
        ), actor);

        assertEquals("course-java-001", result.get("course_id"));
        assertEquals("class-cs-2026", result.get("class_id"));
        verify(jdbcTemplate).update(contains("INSERT INTO course_class"),
                any(), eq("course-java-001"), eq("class-cs-2026"), eq("teacher001"), eq("2026 春季"), eq("开课中"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_COURSE_CLASS"));
    }

    @Test
    void createRejectsMissingTeacher() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("course-java-001"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("class-cs-2026"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("missing-teacher"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> courseClassService.create(Map.of(
                        "course_id", "course-java-001",
                        "class_id", "class-cs-2026",
                        "teacher_id", "missing-teacher",
                        "semester", "2026 春季",
                        "status", "开课中"
                ), user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void updateChangesStatusAndTeacher() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(contains("FROM course_class"), eq(Integer.class), eq("class-java-001")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM sys_user"), eq(Integer.class), eq("teacher002")))
                .thenReturn(1);

        Map<String, Object> result = courseClassService.update("class-java-001", Map.of(
                "teacher_id", "teacher002",
                "semester", "2026 秋季",
                "status", "停用"
        ), actor);

        assertEquals("class-java-001", result.get("course_class_id"));
        assertEquals("停用", result.get("status"));
        verify(jdbcTemplate).update(contains("UPDATE course_class SET status = ?"),
                eq("停用"), eq("teacher002"), eq("2026 秋季"), eq("class-java-001"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("UPDATE_COURSE_CLASS"));
    }

    @Test
    void listRejectsInvalidStatus() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> courseClassService.list(null, null, null, "已结课", 1, 10,
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
