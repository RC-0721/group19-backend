package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AIInterviewServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final AIInterviewService interviewService = new AIInterviewService(jdbcTemplate);

    @Test
    void startRejectsMissingJob() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing"))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.start(Map.of(
                        "job_id", "missing",
                        "scene", "模拟面试",
                        "difficulty_level", "初级",
                        "prompt_version", "v1"
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
    }

    @Test
    void startCreatesSession() {
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend", "job_name", "Java 后端")));

        Map<String, Object> result = interviewService.start(Map.of(
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "difficulty_level", "初级",
                "prompt_version", "v1"
        ), user("student001", "STUDENT"));

        assertEquals("已创建", result.get("status"));
    }

    @Test
    void sendMessageRejectsOtherStudent() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1"
        )));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.sendMessage("session-1", Map.of("message_content", "回答"),
                        user("student002", "STUDENT")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void sendMessageCreatesReportAndEvidence() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1"
        )));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("chunk_text", "知识片段")));
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend"))).thenReturn(List.of(Map.of("skill_id", "skill-1")));

        Map<String, Object> result = interviewService.sendMessage(
                "session-1", Map.of("message_content", "回答"), user("student001", "STUDENT"));

        assertEquals("已完成", result.get("status"));
    }

    @Test
    void reportAllowsTeacherInScope() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "report_id", "report-1",
                "job_id", "job-java-backend",
                "score", 82.0,
                "strength", "优势",
                "weakness", "短板",
                "suggestion", "建议",
                "student_id", "student001"
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = interviewService.report("session-1", user("teacher001", "TEACHER"));

        assertEquals("report-1", result.get("report_id"));
    }

    @Test
    void reportRejectsTeacherOutOfStudentProfileScope() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "report_id", "report-1",
                "job_id", "job-java-backend",
                "score", 82.0,
                "strength", "优势",
                "weakness", "短板",
                "suggestion", "建议",
                "student_id", "student002"
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student002"), eq("teacher001"))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.report("session-1", user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void listReturnsStudentSessions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student001"), eq("job-java-backend")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("student001"), eq("job-java-backend"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of("session_id", "session-1")));

        Map<String, Object> result = interviewService.list(
                null, "job-java-backend", null, 1, 10, user("student001", "STUDENT"));

        assertEquals(1, result.get("total"));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
