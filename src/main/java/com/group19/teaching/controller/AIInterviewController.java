package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AIInterviewService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/api/interviews/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(interviewService.sendMessage(sessionId, request, actor));
    }

    @GetMapping("/api/interviews/sessions/{sessionId}/report")
    public ApiResponse<Map<String, Object>> report(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String sessionId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(interviewService.report(sessionId, actor));
    }
}
