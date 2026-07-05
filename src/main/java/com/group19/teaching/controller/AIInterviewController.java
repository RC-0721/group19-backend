package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AIInterviewService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class AIInterviewController {

    private final AIInterviewService interviewService;
    private final AuthService authService;

    public AIInterviewController(AIInterviewService interviewService, AuthService authService) {
        this.interviewService = interviewService;
        this.authService = authService;
    }

    @GetMapping("/api/interviews/sessions")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "student_id", required = false) String studentId,
            @RequestParam(value = "job_id", required = false) String jobId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(interviewService.list(studentId, jobId, status, pageNo, pageSize, actor));
    }

    @PostMapping("/api/interviews/sessions")
    public ApiResponse<Map<String, Object>> start(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.start(request, actor));
    }

    @GetMapping("/api/interviews/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(interviewService.detail(sessionId, actor));
    }

    @PostMapping("/api/interviews/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.sendMessage(sessionId, request, actor));
    }

    @GetMapping("/api/interviews/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> listMessages(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(interviewService.listMessages(sessionId, actor));
    }

    @PostMapping("/api/interviews/{sessionId}/transcripts")
    public ApiResponse<Map<String, Object>> saveTranscript(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(interviewService.saveTranscript(sessionId, request, actor));
    }

    @GetMapping("/api/interviews/{sessionId}/transcripts")
    public ApiResponse<Map<String, Object>> listTranscripts(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(interviewService.listTranscripts(sessionId, actor));
    }

    @PostMapping("/api/interviews/{sessionId}/media")
    public ApiResponse<Map<String, Object>> bindMedia(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.bindMedia(sessionId, request, actor));
    }

    @PostMapping(value = "/api/interviews/{sessionId}/media/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadMedia(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestParam("media_type") String mediaType,
            @RequestParam(value = "duration", required = false) Double duration,
            @RequestPart("file") MultipartFile file) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.uploadMedia(sessionId, mediaType, duration, file, actor));
    }

    @GetMapping("/api/interviews/{sessionId}/media")
    public ApiResponse<Map<String, Object>> listMedia(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(interviewService.listMedia(sessionId, actor));
    }

    @PostMapping(value = "/api/interviews/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return interviewService.streamMessage(sessionId, request, actor);
    }

    @PostMapping("/api/interviews/sessions/{sessionId}/finish")
    public ApiResponse<Map<String, Object>> finish(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.finish(sessionId, request, actor));
    }

    @PostMapping("/api/interviews/sessions/{sessionId}/report/generate")
    public ApiResponse<Map<String, Object>> generateReport(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.generateReport(sessionId, actor));
    }

    @GetMapping("/api/interviews/sessions/{sessionId}/report")
    public ApiResponse<Map<String, Object>> report(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(interviewService.report(sessionId, actor));
    }
}
