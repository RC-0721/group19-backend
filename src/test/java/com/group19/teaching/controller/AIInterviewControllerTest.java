package com.group19.teaching.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AIInterviewService;
import com.group19.teaching.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(AIInterviewController.class)
class AIInterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AIInterviewService interviewService;

    @MockBean
    private AuthService authService;

    @Test
    void startReturnsFirstQuestion() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.start(anyMap(), eq(student))).thenReturn(Map.of(
                "session_id", "session-1",
                "status", "进行中",
                "first_question", "问题",
                "current_round", 0,
                "round_count", 5,
                "can_generate_report", false
        ));

        mockMvc.perform(post("/api/interviews/sessions")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"job-java-backend\",\"scene\":\"模拟面试\",\"difficulty_level\":\"初级\",\"prompt_version\":\"v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").value("session-1"));
    }

    @Test
    void detailReturnsSession() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(interviewService.detail("session-1", student)).thenReturn(Map.of(
                "session_id", "session-1",
                "student_id", "student001",
                "status", "进行中",
                "current_round", 0,
                "round_count", 5,
                "can_generate_report", false
        ));

        mockMvc.perform(get("/api/interviews/sessions/session-1")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").value("session-1"))
                .andExpect(jsonPath("$.data.current_round").value(0));
    }

    @Test
    void sendMessageReturnsAiMessage() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.sendMessage(eq("session-1"), anyMap(), eq(student))).thenReturn(Map.of(
                "message_id", "msg-1",
                "message_content", "反馈",
                "reference_chunk", "引用",
                "status", "进行中",
                "current_round", 1,
                "round_count", 5,
                "can_generate_report", false
        ));

        mockMvc.perform(post("/api/interviews/session-1/messages")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"回答\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("进行中"))
                .andExpect(jsonPath("$.data.current_round").value(1));
    }

    @Test
    void listMessagesReturnsMessages() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(interviewService.listMessages("session-1", student)).thenReturn(Map.of(
                "messages", List.of(Map.of("message_id", "msg-1", "sender_type", "STUDENT"))
        ));

        mockMvc.perform(get("/api/interviews/session-1/messages")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].message_id").value("msg-1"));
    }

    @Test
    void listSessionMessagesReturnsMessages() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(interviewService.listMessages("session-1", student)).thenReturn(Map.of(
                "session", Map.of("session_id", "session-1"),
                "messages", List.of(Map.of("message_id", "msg-1", "role", "user"))
        ));

        mockMvc.perform(get("/api/interviews/sessions/session-1/messages")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.session_id").value("session-1"))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"));
    }

    @Test
    void saveTranscriptReturnsSegmentId() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(student);
        when(interviewService.saveTranscript(eq("session-1"), anyMap(), eq(student))).thenReturn(Map.of(
                "segment_id", "segment-1"
        ));

        mockMvc.perform(post("/api/interviews/session-1/transcripts")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"回答\",\"source\":\"student_text\",\"start_time\":0,\"end_time\":2.5,\"is_final\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segment_id").value("segment-1"));
    }

    @Test
    void listTranscriptsReturnsSegments() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(teacher);
        when(interviewService.listTranscripts("session-1", teacher)).thenReturn(Map.of(
                "segments", List.of(Map.of("segment_id", "segment-1", "content", "回答"))
        ));

        mockMvc.perform(get("/api/interviews/session-1/transcripts")
                        .header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segments[0].segment_id").value("segment-1"));
    }

    @Test
    void bindMediaReturnsMediaId() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.bindMedia(eq("session-1"), anyMap(), eq(student))).thenReturn(Map.of(
                "media_id", "media-1"
        ));

        mockMvc.perform(post("/api/interviews/session-1/media")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"media_type\":\"video\",\"file_name\":\"interview.webm\",\"file_url\":\"/uploads/interview.webm\",\"mime_type\":\"video/webm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media_id").value("media-1"));
    }

    @Test
    void listMediaReturnsMediaList() throws Exception {
        User admin = user("admin001", "EDU_ADMIN");
        when(authService.requireRole("admin-token", "STUDENT", "TEACHER", "EDU_ADMIN")).thenReturn(admin);
        when(interviewService.listMedia("session-1", admin)).thenReturn(Map.of(
                "media", List.of(Map.of("media_id", "media-1", "media_type", "video"))
        ));

        mockMvc.perform(get("/api/interviews/session-1/media")
                        .header("token", "admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media[0].media_id").value("media-1"));
    }

    @Test
    void uploadMediaReturnsMediaId() throws Exception {
        User student = user("student001", "STUDENT");
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1, 2, 3});
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.uploadMedia(eq("session-1"), eq("screenshot"), eq(null), eq(file), eq(student)))
                .thenReturn(Map.of(
                        "media_id", "media-1",
                        "file_url", "/uploads/interviews/session-1/media-1.png",
                        "storage_path", "data/uploads/interviews/session-1/media-1.png",
                        "mime_type", "image/png"
                ));

        mockMvc.perform(multipart("/api/interviews/session-1/media/upload")
                        .file(file)
                        .header("token", "student-token")
                        .param("media_type", "screenshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media_id").value("media-1"));
    }

    @Test
    void streamMessageStartsSseResponse() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.streamChat(eq("session-1"), anyMap(), eq(student))).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/interviews/session-1/messages/stream")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"回答\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void streamChatStartsSseResponse() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.streamChat(eq("session-1"), anyMap(), eq(student))).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/interviews/sessions/session-1/chat/stream")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"回答\",\"source\":\"student_audio_stt\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void finishReturnsCompletedSession() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.finish(eq("session-1"), anyMap(), eq(student))).thenReturn(Map.of(
                "session_id", "session-1",
                "status", "已完成",
                "current_round", 2,
                "round_count", 5,
                "can_generate_report", true
        ));

        mockMvc.perform(post("/api/interviews/sessions/session-1/finish")
                        .header("token", "student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finish_reason\":\"student_finished\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已完成"));
    }

    @Test
    void generateReportReturnsReport() throws Exception {
        User student = user("student001", "STUDENT");
        when(authService.requireRole("student-token", "STUDENT")).thenReturn(student);
        when(interviewService.generateReport("session-1", student)).thenReturn(Map.of(
                "report_id", "report-1",
                "session_id", "session-1",
                "job_id", "job-java-backend",
                "overall_score", 82.0,
                "expression_score", 80.0
        ));

        mockMvc.perform(post("/api/interviews/sessions/session-1/report/generate")
                        .header("token", "student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report_id").value("report-1"))
                .andExpect(jsonPath("$.data.overall_score").value(82.0));
    }

    @Test
    void reportReturnsScore() throws Exception {
        User teacher = user("teacher001", "TEACHER");
        when(authService.requireRole("teacher-token", "STUDENT", "TEACHER")).thenReturn(teacher);
        when(interviewService.report("session-1", teacher)).thenReturn(Map.of(
                "report_id", "report-1",
                "job_id", "job-java-backend",
                "score", 82.0,
                "strength", "优势",
                "weakness", "短板",
                "suggestion", "建议",
                "overall_score", 82.0
        ));

        mockMvc.perform(get("/api/interviews/sessions/session-1/report")
                        .header("token", "teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(82.0))
                .andExpect(jsonPath("$.data.overall_score").value(82.0));
    }

    private static User user(String account, String role) {
        User user = new User();
        user.setAccount(account);
        user.setRole(role);
        return user;
    }
}
