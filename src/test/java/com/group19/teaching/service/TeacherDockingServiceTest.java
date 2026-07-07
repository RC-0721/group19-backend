package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TeacherDockingServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final TeacherDockingService teacherDockingService = new TeacherDockingService(jdbcTemplate);

    @Test
    void optionsReturnsTeacherScopedDropdowns() {
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM course c"), eq("t2018015")))
                .thenReturn(List.of(Map.of("course_id", "course-java-001", "course_name", "Java EE", "status", "已发布")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM course_class cc"), eq("t2018015")))
                .thenReturn(List.of(Map.of("course_class_id", "cc-java-001", "course_id", "course-java-001")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM chapter ch"), eq("t2018015")))
                .thenReturn(List.of(Map.of("chapter_id", "ch-1", "chapter_order", 1)));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM course_material m"), eq("t2018015")))
                .thenReturn(List.of(Map.of("material_id", "material-1")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM job_direction")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend", "job_name", "后端开发", "status", "启用")));

        Map<String, Object> result = teacherDockingService.options(user("t2018015", "TEACHER"));

        assertEquals(1, ((List<?>) result.get("courses")).size());
        assertEquals(1, ((List<?>) result.get("course_classes")).size());
        assertEquals(1, ((List<?>) result.get("chapters")).size());
        assertEquals(1, ((List<?>) result.get("materials")).size());
        assertEquals(1, ((List<?>) result.get("jobs")).size());
    }

    @Test
    void dashboardReturnsTeacherScopedSummary() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("t2018015"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM job_direction"), eq(Integer.class), any(Object[].class)))
                .thenReturn(2);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM homework h"), eq("t2018015")))
                .thenReturn(List.of(Map.of("homework_id", "hw-1")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM project_task pt"), eq("t2018015")))
                .thenReturn(List.of(Map.of("project_task_id", "project-1")));

        Map<String, Object> result = teacherDockingService.dashboard(user("t2018015", "TEACHER"));

        assertEquals(1, result.get("course_count"));
        assertEquals(2, result.get("available_job_count"));
        assertEquals(1, ((List<?>) result.get("recent_homeworks")).size());
        assertEquals(1, ((List<?>) result.get("recent_projects")).size());
    }

    @Test
    void profileAnalysisOptionsMarksEvidenceRichRowRecommended() {
        when(jdbcTemplate.queryForList(contains("sp.target_job_id AS job_id"), eq("t2018015")))
                .thenReturn(List.of(
                        Map.of(
                                "course_class_id", "cc-1",
                                "course_id", "course-java-001",
                                "class_id", "class-1",
                                "job_id", "job-java-backend",
                                "student_count", 1,
                                "evidence_count", 2
                        ),
                        Map.of(
                                "course_class_id", "cc-2",
                                "course_id", "course-java-002",
                                "class_id", "class-2",
                                "job_id", "job-java-backend",
                                "student_count", 3,
                                "evidence_count", 0
                        )
                ));

        Map<String, Object> result = teacherDockingService.profileAnalysisOptions(user("t2018015", "TEACHER"));

        List<?> items = (List<?>) result.get("items");
        assertEquals(2, items.size());
        assertEquals(true, ((Map<?, ?>) items.get(0)).get("recommended"));
        assertEquals(false, ((Map<?, ?>) items.get(1)).get("recommended"));
    }

    @Test
    void profileAnalysisOptionsFallsBackToFirstStudentRow() {
        when(jdbcTemplate.queryForList(contains("sp.target_job_id AS job_id"), eq("t2018015")))
                .thenReturn(List.of(Map.of(
                        "course_class_id", "cc-1",
                        "course_id", "course-java-001",
                        "class_id", "class-1",
                        "job_id", "job-java-backend",
                        "student_count", 1,
                        "evidence_count", 0
                )));

        Map<String, Object> result = teacherDockingService.profileAnalysisOptions(user("t2018015", "TEACHER"));

        assertEquals(true, ((Map<?, ?>) ((List<?>) result.get("items")).get(0)).get("recommended"));
    }

    @Test
    void rejectsNonTeacher() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> teacherDockingService.options(user("20230301", "STUDENT")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
