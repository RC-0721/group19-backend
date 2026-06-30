package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.QuestionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final AuthService authService;

    public QuestionController(QuestionService questionService, AuthService authService) {
        this.questionService = questionService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "knowledge_id", required = false) String knowledgeId,
            @RequestParam(value = "source_id", required = false) String sourceId,
            @RequestParam(value = "job_id", required = false) String jobId,
            @RequestParam(value = "tech_id", required = false) String techId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "question_type", required = false) String questionType,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(questionService.list(
                knowledgeId, sourceId, jobId, techId, keyword, questionType, difficulty, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(questionService.create(request, actor));
    }

    @PutMapping("/{questionId}/audit")
    public ApiResponse<Map<String, Object>> audit(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(questionService.audit(questionId, String.valueOf(request.get("audit_status")), actor));
    }
}
