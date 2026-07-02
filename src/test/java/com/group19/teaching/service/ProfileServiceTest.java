package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProfileServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final ProfileService profileService = new ProfileService(jdbcTemplate);

    @Test
    void getRejectsOtherStudent() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.get("student001", "job-java-backend", user("student002", "STUDENT")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void getRejectsTeacherOutOfScope() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student001"), eq("teacher002"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.get("student001", "job-java-backend", user("teacher002", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void getAllowsTeacherByStudentProfileClass() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student002"), eq("teacher001"))).thenReturn(1);
        when(jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", "job-java-backend"))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));
        when(jdbcTemplate.queryForList(anyString(), eq("student002"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("student002"), eq("job-java-backend"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.startsWith("profile-"))).thenReturn(List.of());

        Map<String, Object> result = profileService.get("student002", "job-java-backend", user("teacher001", "TEACHER"));

        assertEquals("数据不足", result.get("knowledge_mastery"));
    }

    @Test
    void getCreatesProfile() {
        when(jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", "job-java-backend"))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));
        when(jdbcTemplate.queryForList(anyString(), eq("student001"))).thenReturn(List.of(Map.of(
                "evidence_id", "evidence-1",
                "score", 82.0
        )));
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq("job-java-backend"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.startsWith("profile-"))).thenReturn(List.of());

        Map<String, Object> result = profileService.get("student001", "job-java-backend", user("student001", "STUDENT"));

        assertEquals(true, String.valueOf(result.get("profile_id")).startsWith("profile-"));
        assertEquals(1, ((List<?>) result.get("evidences")).size());
    }

    @Test
    void classAnalysisReturnsEvidence() {
        when(jdbcTemplate.queryForList("SELECT class_id FROM class WHERE class_id = ? LIMIT 1", "class-cs-2026"))
                .thenReturn(List.of(Map.of("class_id", "class-cs-2026")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("class-cs-2026"), eq("teacher001"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("class-cs-2026"))).thenReturn(List.of(Map.of(
                "student_id", "student001",
                "source_type", "AI_INTERVIEW",
                "source_id", "report-1",
                "knowledge_id", "kp-001",
                "skill_id", "skill-java-001",
                "score", 82.0
        )));

        Map<String, Object> result = profileService.classAnalysis(
                "class-cs-2026", null, null, user("teacher001", "TEACHER"));

        assertEquals(1, ((List<?>) result.get("evidences")).size());
    }

    @Test
    void classReportCsvContainsEvidenceRows() {
        when(jdbcTemplate.queryForList("SELECT class_id FROM class WHERE class_id = ? LIMIT 1", "class-cs-2026"))
                .thenReturn(List.of(Map.of("class_id", "class-cs-2026")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("class-cs-2026"), eq("teacher001"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("class-cs-2026"))).thenReturn(List.of(Map.of(
                "student_id", "student001",
                "source_type", "AI_INTERVIEW",
                "source_id", "report-1",
                "knowledge_id", "kp-001",
                "skill_id", "skill-java-001",
                "score", 82.0
        )));

        String csv = profileService.classReportCsv("class-cs-2026", null, null, user("teacher001", "TEACHER"));

        assertEquals(true, csv.contains("evidence,student001,AI_INTERVIEW,report-1,kp-001,skill-java-001,82.0,"));
    }

    @Test
    void classAnalysisRejectsTeacherOutOfScope() {
        when(jdbcTemplate.queryForList("SELECT class_id FROM class WHERE class_id = ? LIMIT 1", "class-cs-2026"))
                .thenReturn(List.of(Map.of("class_id", "class-cs-2026")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("class-cs-2026"), eq("teacher002"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.classAnalysis("class-cs-2026", null, null, user("teacher002", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void classAnalysisRejectsBlankJobParam() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.classAnalysis("class-cs-2026", null, " ", user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void classAnalysisRejectsMissingClass() {
        when(jdbcTemplate.queryForList("SELECT class_id FROM class WHERE class_id = ? LIMIT 1", "missing"))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> profileService.classAnalysis("missing", null, null, user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void classAnalysisFiltersByJob() {
        when(jdbcTemplate.queryForList("SELECT class_id FROM class WHERE class_id = ? LIMIT 1", "class-cs-2026"))
                .thenReturn(List.of(Map.of("class_id", "class-cs-2026")));
        when(jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", "job-java-backend"))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("class-cs-2026"), eq("teacher001"))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("class-cs-2026"), eq("job-java-backend"))).thenReturn(List.of(Map.of(
                "student_id", "student001",
                "source_type", "AI_INTERVIEW",
                "source_id", "report-1",
                "knowledge_id", "",
                "skill_id", "skill-java-001",
                "score", 82.0
        )));

        Map<String, Object> result = profileService.classAnalysis(
                "class-cs-2026", null, "job-java-backend", user("teacher001", "TEACHER"));

        assertEquals(1, ((List<?>) result.get("evidences")).size());
    }

    @Test
    void refreshReturnsProfileStatus() {
        when(jdbcTemplate.queryForList("SELECT job_id FROM job_direction WHERE job_id = ? LIMIT 1", "job-java-backend"))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend")));
        when(jdbcTemplate.queryForList(anyString(), eq("student001"))).thenReturn(List.of(Map.of(
                "evidence_id", "evidence-1",
                "score", 82.0
        )));
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq("job-java-backend"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.startsWith("profile-"))).thenReturn(List.of());

        Map<String, Object> result = profileService.refresh(
                "student001", "job-java-backend", user("student001", "STUDENT"));

        assertEquals("周期更新", result.get("profile_status"));
        verify(jdbcTemplate, times(2)).update(anyString(), eq("student001"));
        verify(jdbcTemplate).update(anyString(), anyString(), eq("student001"), eq("job-java-backend"),
                eq("周期更新"), anyString(), anyString(), any());
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
