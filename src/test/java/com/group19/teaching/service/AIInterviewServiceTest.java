package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.group19.teaching.common.BusinessException;
import com.group19.teaching.common.ErrorCode;
import com.group19.teaching.domain.entity.User;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class AIInterviewServiceTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final AIInterviewService interviewService = new AIInterviewService(jdbcTemplate);

    @TempDir
    Path tempDir;

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

        assertEquals("进行中", result.get("status"));
        assertEquals(0, result.get("current_round"));
        assertEquals(5, result.get("round_count"));
    }

    @Test
    void startAcceptsRoundCount() {
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend", "job_name", "Java 后端")));

        Map<String, Object> result = interviewService.start(Map.of(
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "difficulty_level", "初级",
                "prompt_version", "v1",
                "round_count", 3
        ), user("student001", "STUDENT"));

        assertEquals(3, result.get("round_count"));
    }

    @Test
    void startRejectsInvalidRoundCount() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.start(Map.of(
                        "job_id", "job-java-backend",
                        "scene", "模拟面试",
                        "difficulty_level", "初级",
                        "prompt_version", "v1",
                        "round_count", 21
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void detailReturnsCanGenerateReport() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "model", "mock-ai",
                "prompt_version", "v1",
                "status", "进行中",
                "current_round", 5,
                "round_count", 5
        )));

        Map<String, Object> result = interviewService.detail("session-1", user("student001", "STUDENT"));

        assertEquals(true, result.get("can_generate_report"));
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
    void sendMessageAdvancesRoundWithoutCompletingSession() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1",
                "status", "进行中",
                "current_round", 1,
                "round_count", 3
        )));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("chunk_text", "知识片段")));

        Map<String, Object> result = interviewService.sendMessage(
                "session-1", Map.of("message_content", "回答"), user("student001", "STUDENT"));

        assertEquals("进行中", result.get("status"));
        assertEquals(2, result.get("current_round"));
        assertEquals(false, result.get("can_generate_report"));
    }

    @Test
    void sendMessageMarksReportAvailableAtRoundLimit() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1",
                "status", "进行中",
                "current_round", 2,
                "round_count", 3
        )));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("chunk_text", "知识片段")));

        Map<String, Object> result = interviewService.sendMessage(
                "session-1", Map.of("message_content", "回答"), user("student001", "STUDENT"));

        assertEquals(3, result.get("current_round"));
        assertEquals(true, result.get("can_generate_report"));
    }

    @Test
    void listMessagesReturnsMessagesForOwner() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(session("student001")))
                .thenReturn(List.of(Map.of("message_id", "msg-1", "sender_type", "STUDENT")));

        Map<String, Object> result = interviewService.listMessages("session-1", user("student001", "STUDENT"));

        assertEquals(1, ((List<?>) result.get("messages")).size());
    }

    @Test
    void saveTranscriptCreatesSegmentForOwner() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        Map<String, Object> result = interviewService.saveTranscript("session-1", Map.of(
                "content", "我熟悉 Spring Boot",
                "source", "student_text",
                "start_time", 0,
                "end_time", 3.2,
                "is_final", true
        ), user("student001", "STUDENT"));

        assertTrue(String.valueOf(result.get("segment_id")).startsWith("segment-"));
    }

    @Test
    void saveTranscriptRejectsInvalidTimeRange() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.saveTranscript("session-1", Map.of(
                        "content", "回答",
                        "source", "student_text",
                        "start_time", 5,
                        "end_time", 3,
                        "is_final", true
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
    }

    @Test
    void listTranscriptsAllowsTeacherInScope() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(session("student001")))
                .thenReturn(List.of(Map.of("segment_id", "segment-1", "content", "回答")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("student001"), eq("teacher001"))).thenReturn(1);

        Map<String, Object> result = interviewService.listTranscripts("session-1", user("teacher001", "TEACHER"));

        assertEquals(1, ((List<?>) result.get("segments")).size());
    }

    @Test
    void bindMediaCreatesMediaForOwner() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        Map<String, Object> result = interviewService.bindMedia("session-1", Map.of(
                "media_type", "video",
                "file_name", "interview.webm",
                "file_url", "/uploads/interview.webm",
                "mime_type", "video/webm"
        ), user("student001", "STUDENT"));

        assertTrue(String.valueOf(result.get("media_id")).startsWith("media-"));
    }

    @Test
    void bindMediaRejectsInvalidMimeType() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.bindMedia("session-1", Map.of(
                        "media_type", "video",
                        "file_url", "/uploads/interview.mp4",
                        "mime_type", "video/mp4"
                ), user("student001", "STUDENT")));

        assertEquals(ErrorCode.FILE_INVALID, exception.errorCode());
    }

    @Test
    void bindMediaRejectsTeacher() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.bindMedia("session-1", Map.of(
                        "media_type", "video",
                        "file_url", "/uploads/interview.webm",
                        "mime_type", "video/webm"
                ), user("teacher001", "TEACHER")));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
    }

    @Test
    void uploadMediaCreatesScreenshotForOwner() {
        AIInterviewService service = new AIInterviewService(jdbcTemplate, tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1, 2, 3});
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        Map<String, Object> result = service.uploadMedia(
                "session-1", "screenshot", null, file, user("student001", "STUDENT"));

        assertTrue(String.valueOf(result.get("media_id")).startsWith("media-"));
        assertEquals("image/png", result.get("mime_type"));
    }

    @Test
    void uploadMediaCreatesVideoForOwner() {
        AIInterviewService service = new AIInterviewService(jdbcTemplate, tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "interview.webm", "video/webm", new byte[]{1, 2, 3});
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        Map<String, Object> result = service.uploadMedia(
                "session-1", "video", 12.5, file, user("student001", "STUDENT"));

        assertTrue(String.valueOf(result.get("file_url")).contains("/uploads/interviews/session-1/"));
        assertEquals(12.5, result.get("duration"));
    }

    @Test
    void uploadMediaRejectsInvalidType() {
        AIInterviewService service = new AIInterviewService(jdbcTemplate, tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "bad.exe", "application/octet-stream", new byte[]{1});
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.uploadMedia("session-1", "video", null, file, user("student001", "STUDENT")));

        assertEquals(ErrorCode.FILE_INVALID, exception.errorCode());
    }

    @Test
    void listMediaAllowsAdmin() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(session("student001")))
                .thenReturn(List.of(Map.of("media_id", "media-1", "media_type", "video")));

        Map<String, Object> result = interviewService.listMedia("session-1", user("admin001", "EDU_ADMIN"));

        assertEquals(1, ((List<?>) result.get("media")).size());
    }

    @Test
    void finishCompletesSessionForOwner() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        Map<String, Object> result = interviewService.finish(
                "session-1", Map.of("finish_reason", "student_finished"), user("student001", "STUDENT"));

        assertEquals("已完成", result.get("status"));
        assertEquals(true, result.get("can_generate_report"));
    }

    @Test
    void generateReportCreatesReportForFinishedSession() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(finishedSession("student001")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("skill_id", "skill-1")));

        Map<String, Object> result = interviewService.generateReport("session-1", user("student001", "STUDENT"));

        assertTrue(String.valueOf(result.get("report_id")).startsWith("report-"));
        assertEquals(82.0, result.get("overall_score"));
        assertEquals("job-java-backend", result.get("job_id"));
    }

    @Test
    void generateReportRejectsUnfinishedSessionBeforeRoundLimit() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(session("student001")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interviewService.generateReport("session-1", user("student001", "STUDENT")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.errorCode());
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

    private static Map<String, Object> session(String studentId) {
        return Map.of(
                "session_id", "session-1",
                "student_id", studentId,
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1",
                "status", "进行中",
                "current_round", 0,
                "round_count", 5
        );
    }

    private static Map<String, Object> finishedSession(String studentId) {
        return Map.of(
                "session_id", "session-1",
                "student_id", studentId,
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1",
                "status", "已完成",
                "current_round", 2,
                "round_count", 5
        );
    }
}
