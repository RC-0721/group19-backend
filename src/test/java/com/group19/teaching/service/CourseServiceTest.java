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

class CourseServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final CourseService courseService = new CourseService(jdbcTemplate);

    @Test
    void listForStudentOnlyReturnsOwnCourses() {
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM course c"), eq(Integer.class), eq("student001")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM course c"), eq("student001"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of("course_id", "course-java-001", "status", "已发布")));

        Map<String, Object> result = courseService.list(null, null, null, 1, 10,
                user(1L, "student001", "STUDENT"));

        assertEquals(1, result.get("total"));
        verify(jdbcTemplate).queryForObject(contains("sp.student_id = ?"), eq(Integer.class),
                eq("student001"));
        verify(jdbcTemplate).queryForList(contains("sp.student_id = ?"),
                eq("student001"), eq(10), eq(0));
    }

    @Test
    void createInsertsCourseAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("major-cs"))).thenReturn(1);

        Map<String, Object> result = courseService.create(Map.of(
                "course_name", "AI 工程实践",
                "course_code", "AI-001",
                "major_id", "major-cs",
                "credit", 3,
                "status", "已发布"
        ), actor);

        assertEquals("AI 工程实践", result.get("course_name"));
        assertEquals("已发布", result.get("status"));
        verify(jdbcTemplate).update(contains("INSERT INTO course"),
                any(), eq("AI 工程实践"), eq("AI-001"), eq("major-cs"), eq(3.0), eq(""), eq(""), eq(""), eq("已发布"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_COURSE"));
    }

    @Test
    void updateRejectsMissingCourse() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("missing-course"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> courseService.update("missing-course", Map.of("status", "停用"),
                        user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void detailRejectsTeacherOutsideOwnClass() {
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString())).thenReturn(List.of(Map.of(
                "course_id", "course-java-001",
                "course_class_id", "class-java-001",
                "teacher_id", "teacher001"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> courseService.detail("course-java-001", "class-java-001", user("teacher002", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void createChapterInsertsChapterAndWritesOperationLog() {
        User actor = user(2L, "teacher001", "TEACHER");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("course-java-001"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("teacher_id = ?"), eq(Integer.class),
                eq("course-java-001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = courseService.createChapter("course-java-001", Map.of(
                "chapter_name", "Spring Boot 入门",
                "sort_order", 4,
                "status", "启用"
        ), actor);

        assertEquals("course-java-001", result.get("course_id"));
        assertEquals("启用", result.get("status"));
        verify(jdbcTemplate).update(contains("INSERT INTO chapter"),
                any(), eq("course-java-001"), eq(null), eq("Spring Boot 入门"), eq(4), eq("启用"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("2"), eq("TEACHER"), eq("CREATE_CHAPTER"));
    }

    @Test
    void listRejectsInvalidPage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> courseService.list(null, null, null, 0, 10, user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    private static User user(String account, String role) {
        return user(null, account, role);
    }

    private static User user(Long id, String account, String role) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
