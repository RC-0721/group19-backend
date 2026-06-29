package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
