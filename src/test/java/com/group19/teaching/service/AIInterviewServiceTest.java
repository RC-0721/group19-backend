package com.group19.teaching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        assertEquals("请先进行自我介绍，介绍你的学习背景、项目经验和目标岗位相关技术。", result.get("first_question"));
    }

    @Test
    void startDoesNotCallAiBeforeStudentIntroduction() {
        AiService aiService = org.mockito.Mockito.mock(AiService.class);
        AIInterviewService service = new AIInterviewService(jdbcTemplate, aiService);
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_id", "job-java-backend", "job_name", "Java 后端")));

        service.start(Map.of(
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "difficulty_level", "初级",
                "prompt_version", "v1"
        ), user("student001", "STUDENT"));

        verify(aiService, never()).chat(any(), any());
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
    void sendMessageAcceptsContentSourceAndReturnsAliases() {
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1",
                "status", "进行中",
                "current_round", 0,
                "round_count", 5
        )));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("chunk_text", "知识片段")));

        Map<String, Object> result = interviewService.sendMessage(
                "session-1",
                Map.of("content", "回答", "source", "student_audio_stt", "client_message_id", "client-1"),
                user("student001", "STUDENT"));

        assertEquals("assistant", result.get("role"));
        assertEquals("ai", result.get("sender_type"));
        assertEquals("ai_stream", result.get("source"));
        assertEquals(1, result.get("round_no"));
        assertEquals(result.get("message_content"), result.get("content"));
    }

    @Test
    void firstStudentMessageSendsInterviewerSystemPrompt() {
        AiService aiService = org.mockito.Mockito.mock(AiService.class);
        AIInterviewService service = new AIInterviewService(jdbcTemplate, aiService);
        User actor = user("student001", "STUDENT");
        when(jdbcTemplate.queryForList(anyString(), eq("session-1"))).thenReturn(List.of(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "job_id", "job-java-backend",
                "scene", "模拟面试",
                "prompt_version", "v1",
                "status", "进行中",
                "current_round", 0,
                "round_count", 5
        )));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("chunk_text", "知识片段")));
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_name", "Java 后端")));
        when(aiService.chat(any(), eq(actor))).thenReturn(Map.of(
                "model", "deepseek-test",
                "content", "感谢你的介绍，请说明一个你最熟悉的后端项目。"
        ));

        Map<String, Object> result = service.sendMessage(
                "session-1",
                Map.of("message_content", "我是张三，熟悉 Java 后端项目。"),
                actor);

        assertEquals("感谢你的介绍，请说明一个你最熟悉的后端项目。", result.get("content"));
        verify(aiService).chat(argThat(request ->
                "AI_INTERVIEW_CHAT".equals(request.get("scene"))
                        && String.valueOf(request.get("system_prompt")).contains("AI 面试官")
                        && String.valueOf(request.get("system_prompt")).contains("禁止主动输出任何开场回答")
                        && String.valueOf(request.get("prompt")).contains("这是学生首次输入")
        ), eq(actor));
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
                .thenReturn(List.of(Map.of(
                        "session_id", "session-1",
                        "student_id", "student001",
                        "student_name", "学生一",
                        "job_id", "job-java-backend",
                        "job_name", "Java 后端",
                        "status", "进行中")))
                .thenReturn(List.of(Map.of(
                        "message_id", "msg-1",
                        "session_id", "session-1",
                        "sender_type", "STUDENT",
                        "message_content", "回答",
                        "round_no", 1)));

        Map<String, Object> result = interviewService.listMessages("session-1", user("student001", "STUDENT"));

        assertEquals(1, ((List<?>) result.get("messages")).size());
        assertEquals("session-1", ((Map<?, ?>) result.get("session")).get("session_id"));
        Map<?, ?> message = (Map<?, ?>) ((List<?>) result.get("messages")).get(0);
        assertEquals("user", message.get("role"));
        assertEquals("回答", message.get("content"));
        assertEquals("student_text", message.get("source"));
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
        AiService aiService = org.mockito.Mockito.mock(AiService.class);
        AIInterviewService service = new AIInterviewService(jdbcTemplate, aiService);
        User actor = user("student001", "STUDENT");
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(finishedSession("student001")))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        Map.of("sender_type", "student", "message_content", "我熟悉 Spring Boot 和 Redis，做过缓存优化项目。", "source", "student_text", "round_no", 1),
                        Map.of("sender_type", "ai", "message_content", "请说明缓存穿透处理。", "source", "ai_stream", "round_no", 1)))
                .thenReturn(List.of(Map.of("content", "我用布隆过滤器和空值缓存处理缓存穿透。", "source", "student_text", "start_time", 0, "end_time", 3, "is_final", true)))
                .thenReturn(List.of(Map.of("media_type", "video", "file_name", "interview.webm", "mime_type", "video/webm", "duration", 30)));
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_name", "Java 后端")))
                .thenReturn(List.of(Map.of("skill_id", "skill-1")));
        when(aiService.chat(any(), eq(actor))).thenReturn(Map.of(
                "model", "deepseek-test",
                "content", """
                        ```json
                        {"dimension_scores":{"knowledge":7,"depth":6,"project":8,"logic":7,"communication":6,"reflection":5},"overall_score":7,"summary":"基础扎实，项目表达较好。"}
                        ```
                        """
        ));

        Map<String, Object> result = service.generateReport("session-1", actor);

        assertTrue(String.valueOf(result.get("report_id")).startsWith("report-"));
        assertEquals(65.0, result.get("overall_score"));
        assertEquals(70.0, result.get("knowledge_score"));
        assertEquals(60.0, result.get("depth_score"));
        assertEquals(80.0, result.get("project_score"));
        assertEquals(70.0, result.get("logic_score"));
        assertEquals(60.0, result.get("communication_score"));
        assertEquals(50.0, result.get("reflection_score"));
        assertEquals(60.0, result.get("expression_score"));
        assertEquals(65.0, result.get("technical_score"));
        assertEquals(65.0, result.get("score"));
        assertEquals(6, ((List<?>) result.get("score_dimensions")).size());
        assertEquals(70.0, ((Map<?, ?>) result.get("dimension_scores")).get("knowledge"));
        assertEquals("job-java-backend", result.get("job_id"));
        verify(aiService).chat(argThat(request ->
                "AI_INTERVIEW_REPORT".equals(request.get("scene"))
                        && String.valueOf(request.get("prompt")).contains("面试消息")
                        && String.valueOf(request.get("prompt")).contains("转写片段")
                        && String.valueOf(request.get("prompt")).contains("媒体信息")
        ), eq(actor));
    }

    @Test
    void generateReportClampsMissingDimensionScores() {
        AiService aiService = org.mockito.Mockito.mock(AiService.class);
        AIInterviewService service = new AIInterviewService(jdbcTemplate, aiService);
        User actor = user("student001", "STUDENT");
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(finishedSession("student001")))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_name", "Java 后端")))
                .thenReturn(List.of(Map.of("skill_id", "skill-1")));
        when(aiService.chat(any(), eq(actor))).thenReturn(Map.of(
                "model", "deepseek-test",
                "content", "{\"dimension_scores\":{\"knowledge\":12,\"project\":0,\"logic\":7,\"communication\":6,\"reflection\":5},\"overall_score\":4,\"summary\":\"有缺失分。\"}"
        ));

        Map<String, Object> result = service.generateReport("session-1", actor);

        assertEquals(100.0, result.get("knowledge_score"));
        assertEquals(40.0, result.get("depth_score"));
        assertEquals(10.0, result.get("project_score"));
        assertEquals(55.0, result.get("overall_score"));
    }

    @Test
    void generateReportRejectsInvalidAiJson() {
        AiService aiService = org.mockito.Mockito.mock(AiService.class);
        AIInterviewService service = new AIInterviewService(jdbcTemplate, aiService);
        User actor = user("student001", "STUDENT");
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(finishedSession("student001")))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_name", "Java 后端")));
        when(aiService.chat(any(), eq(actor))).thenReturn(Map.of("model", "deepseek-test", "content", "not json"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generateReport("session-1", actor));

        assertEquals(ErrorCode.AI_UNAVAILABLE, exception.errorCode());
    }

    @Test
    void generateReportPropagatesAiUnavailable() {
        AiService aiService = org.mockito.Mockito.mock(AiService.class);
        AIInterviewService service = new AIInterviewService(jdbcTemplate, aiService);
        User actor = user("student001", "STUDENT");
        when(jdbcTemplate.queryForList(anyString(), eq("session-1")))
                .thenReturn(List.of(finishedSession("student001")))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend")))
                .thenReturn(List.of(Map.of("job_name", "Java 后端")));
        when(aiService.chat(any(), eq(actor))).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generateReport("session-1", actor));

        assertEquals(ErrorCode.AI_UNAVAILABLE, exception.errorCode());
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
        assertEquals(6, ((List<?>) result.get("score_dimensions")).size());
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

    @Test
    void listAllowsEduAdminSessions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("job-java-backend")))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq("job-java-backend"), eq(10), eq(0)))
                .thenReturn(List.of(Map.of(
                        "session_id", "session-1",
                        "student_name", "学生一",
                        "job_name", "Java 后端",
                        "last_message", "最近回复"
                )));

        Map<String, Object> result = interviewService.list(
                null, "job-java-backend", null, 1, 10, user("admin001", "EDU_ADMIN"));

        assertEquals(1, result.get("total"));
        Map<?, ?> record = (Map<?, ?>) ((List<?>) result.get("records")).get(0);
        assertEquals("学生一", record.get("student_name"));
        assertEquals("最近回复", record.get("last_message"));
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
