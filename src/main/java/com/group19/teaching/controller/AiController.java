package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AiContentService;
import com.group19.teaching.service.AiService;
import com.group19.teaching.service.AiTaskService;
import com.group19.teaching.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class AiController {

    private final AiService aiService;
    private final AiTaskService aiTaskService;
    private final AiContentService aiContentService;
    private final AuthService authService;

    public AiController(
            AiService aiService,
            AiTaskService aiTaskService,
            AiContentService aiContentService,
            AuthService authService) {
        this.aiService = aiService;
        this.aiTaskService = aiTaskService;
        this.aiContentService = aiContentService;
        this.authService = authService;
    }

    @PostMapping("/api/ai/chat")
    public ApiResponse<Map<String, Object>> chat(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiService.chat(request, actor));
    }

    @PostMapping("/api/ai/chat/stream")
    public SseEmitter streamChat(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return aiService.stream(request, actor);
    }

    @PostMapping("/api/ai/tasks")
    public ApiResponse<Map<String, Object>> createTask(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiTaskService.submit(request, actor));
    }

    @GetMapping("/api/ai/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String taskId) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiTaskService.get(taskId, actor));
    }

    @PostMapping("/api/ai/parse-material")
    public ApiResponse<Map<String, Object>> parseMaterial(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.parseMaterial(request, actor));
    }

    @GetMapping("/api/ai/parse-material/{taskId}")
    public ApiResponse<Map<String, Object>> getParseMaterial(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String taskId) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.getParseTask(taskId, actor));
    }

    @PutMapping("/api/ai/parse-material/{taskId}/confirm")
    public ApiResponse<Map<String, Object>> confirmParseMaterial(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String taskId) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.confirmParseTask(taskId, actor));
    }

    @PostMapping("/api/ai/build-knowledge-graph")
    public ApiResponse<Map<String, Object>> buildKnowledgeGraph(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.buildKnowledgeGraph(request, actor));
    }

    @GetMapping("/api/ai/knowledge-graph/candidates")
    public ApiResponse<Map<String, Object>> knowledgeGraphCandidates(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "audit_status", required = false) String auditStatus,
            @RequestParam(value = "page_no", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.listKnowledgeCandidates(courseId, auditStatus, pageNo, pageSize, actor));
    }

    @PutMapping("/api/ai/knowledge-graph/candidates/{id}/approve")
    public ApiResponse<Map<String, Object>> approveKnowledgeGraphCandidate(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String id) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.approveKnowledgeCandidate(id, actor));
    }

    @PostMapping("/api/ai/generate-questions")
    public ApiResponse<Map<String, Object>> generateQuestions(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.generateQuestions(request, actor));
    }

    @GetMapping("/api/ai/question-candidates")
    public ApiResponse<Map<String, Object>> questionCandidates(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "knowledge_id", required = false) String knowledgeId,
            @RequestParam(value = "audit_status", required = false) String auditStatus,
            @RequestParam(value = "page_no", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.listQuestionCandidates(knowledgeId, auditStatus, pageNo, pageSize, actor));
    }

    @PostMapping("/api/ai/question-candidates/{id}/approve")
    public ApiResponse<Map<String, Object>> approveQuestionCandidate(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String id) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.approveQuestionCandidate(id, actor));
    }

    @PostMapping("/api/ai/question-candidates/{id}/reject")
    public ApiResponse<Map<String, Object>> rejectQuestionCandidate(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String id) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(aiContentService.rejectQuestionCandidate(id, actor));
    }
}
