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

class TrainingPlanServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final TrainingPlanService trainingPlanService = new TrainingPlanService(jdbcTemplate);

    @Test
    void createInsertsPlanAndWritesOperationLog() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("major-cs"))).thenReturn(1);

        Map<String, Object> result = trainingPlanService.create(Map.of(
                "major_id", "major-cs",
                "plan_name", "计算机 2026 培养方案",
                "total_credits", 16.5,
                "version", "2026",
                "status", "启用"
        ), actor);

        assertEquals("major-cs", result.get("major_id"));
        assertEquals("启用", result.get("status"));
        verify(jdbcTemplate).update(contains("INSERT INTO training_plan"),
                any(), eq("major-cs"), eq("计算机 2026 培养方案"), eq(16.5), eq("2026"), eq("启用"));
        verify(jdbcTemplate).update(contains("INSERT INTO operation_log"),
                any(), eq("9"), eq("EDU_ADMIN"), eq("CREATE_TRAINING_PLAN"));
    }

    @Test
    void addCourseUpsertsCourseBinding() {
        User actor = user(9L, "admin001", "EDU_ADMIN");
        when(jdbcTemplate.queryForObject(contains("FROM training_plan"), eq(Integer.class),
                eq("training-plan-cs-2026"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM course WHERE"), eq(Integer.class),
                eq("course-java-001"))).thenReturn(1);

        Map<String, Object> result = trainingPlanService.addCourse("training-plan-cs-2026", Map.of(
                "course_id", "course-java-001",
                "is_required", true,
                "suggested_semester", 1,
                "credit", 3
        ), actor);

        assertEquals("course-java-001", result.get("course_id"));
        verify(jdbcTemplate).update(contains("INSERT INTO training_plan_course"),
                any(), eq("training-plan-cs-2026"), eq("course-java-001"), eq(true), eq(1), eq(3.0));
    }

    @Test
    void addPrerequisiteRejectsCycle() {
        when(jdbcTemplate.queryForObject(contains("FROM course WHERE"), eq(Integer.class),
                eq("course-a"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM course WHERE"), eq(Integer.class),
                eq("course-b"))).thenReturn(1);
        when(jdbcTemplate.queryForList(contains("FROM course_prerequisite"))).thenReturn(List.of(Map.of(
                "course_id", "course-b",
                "prerequisite_course_id", "course-a"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> trainingPlanService.addPrerequisite(Map.of(
                        "course_id", "course-a",
                        "prerequisite_course_id", "course-b"
                ), user(9L, "admin001", "EDU_ADMIN")));

        assertEquals(ErrorCode.STATE_NOT_ALLOWED, exception.errorCode());
    }

    private static User user(Long id, String account, String role) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
