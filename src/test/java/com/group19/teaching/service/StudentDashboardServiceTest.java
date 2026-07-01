package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class StudentDashboardServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final StudentDashboardService studentDashboardService = new StudentDashboardService(jdbcTemplate);

    @Test
    void loadReturnsRealAggregatedDashboard() {
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM student_profile sp"), eq("student001")))
                .thenReturn(List.of(Map.of(
                        "student_id", "student001",
                        "class_id", "class-cs-2026",
                        "target_job_id", "job-java-backend",
                        "job_name", "Java 后端开发工程师"
                )));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM pre_task pt"), eq("student001"), eq("class-cs-2026")))
                .thenReturn(List.of(Map.of(
                        "task_id", "pre-java-001",
                        "task_type", "PRE_TASK",
                        "title", "阅读 Java 基础",
                        "deadline", Timestamp.valueOf(LocalDateTime.now().plusDays(1)),
                        "course_id", "course-java-001",
                        "course_class_id", "class-java-001"
                )));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM homework h"), eq("student001"), eq("class-cs-2026")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM project_task pt"), eq("student001"), eq("class-cs-2026")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("JOIN course_class cc"), eq("student001")))
                .thenReturn(List.of(Map.of(
                        "course_id", "course-java-001",
                        "course_class_id", "class-java-001",
                        "course_name", "Java 后端就业能力课程"
                )));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("(SELECT COUNT(*) FROM pre_task"),
                eq("class-java-001"), eq("class-java-001"), eq("course-java-001")))
                .thenReturn(List.of(Map.of("value_count", 4)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("(SELECT COUNT(DISTINCT pts.pre_task_id)"),
                eq("student001"), eq("class-java-001"), eq("student001"), eq("class-java-001"),
                eq("student001"), eq("course-java-001")))
                .thenReturn(List.of(Map.of("value_count", 2)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM practice_record"), eq("student001")))
                .thenReturn(List.of(Map.of("finished_count", 3, "accuracy", 67)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM wrong_book wb"), eq("student001")))
                .thenReturn(List.of(Map.of("weak_point", "MySQL 基础")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM project_submission\n                WHERE student_id"), eq("student001")))
                .thenReturn(List.of(Map.of("submitted_count", 1)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("SELECT submit_status"), eq("student001")))
                .thenReturn(List.of(Map.of("submit_status", "已反馈")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM ai_session"), eq("student001")))
                .thenReturn(List.of(Map.of("completed_count", 1)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM ai_interview_report"), eq("student001")))
                .thenReturn(List.of(Map.of("score", 82.0)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM ability_profile"), eq("student001"), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("profile_id", "profile-1", "profile_status", "初始画像")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM learning_recommendation"), eq("student001"), eq("profile-1")))
                .thenReturn(List.of(Map.of("recommend_content", "补做岗位题目")));

        Map<String, Object> result = studentDashboardService.load(user("student001"));

        assertEquals(1, ((List<?>) result.get("courses")).size());
        assertEquals("阅读 Java 基础", ((Map<?, ?>) ((List<?>) result.get("courses")).get(0)).get("next_task"));
        assertEquals(50, ((Map<?, ?>) ((List<?>) result.get("courses")).get(0)).get("progress"));
        assertEquals("MySQL 基础", ((Map<?, ?>) result.get("practice_summary")).get("weak_point"));
        assertEquals("补做岗位题目", ((Map<?, ?>) result.get("profile_summary")).get("recommendation"));
    }

    @Test
    void loadReturnsDefaultsWhenStudentProfileMissing() {
        when(jdbcTemplate.queryForList(anyString(), eq("student404"))).thenReturn(List.of());

        Map<String, Object> result = studentDashboardService.load(user("student404"));

        assertEquals(List.of(), result.get("courses"));
        assertEquals(List.of(), result.get("todo_tasks"));
        assertEquals(0, ((Map<?, ?>) result.get("practice_summary")).get("finished_count"));
        assertEquals(0, ((Map<?, ?>) result.get("project_summary")).get("submitted_count"));
        assertEquals(0, ((Map<?, ?>) result.get("interview_summary")).get("completed_count"));
        assertEquals("数据不足", ((Map<?, ?>) result.get("profile_summary")).get("profile_status"));
    }

    private static User user(String account) {
        User user = new User();
        user.setAccount(account);
        user.setRole("STUDENT");
        return user;
    }
}
