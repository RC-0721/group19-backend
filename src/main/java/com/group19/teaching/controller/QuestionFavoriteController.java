package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.QuestionFavoriteService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/question-favorites")
public class QuestionFavoriteController {

    private final QuestionFavoriteService questionFavoriteService;
    private final AuthService authService;

    public QuestionFavoriteController(QuestionFavoriteService questionFavoriteService, AuthService authService) {
        this.questionFavoriteService = questionFavoriteService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(questionFavoriteService.list(pageNo, pageSize, actor));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        Object questionId = request.get("question_id");
        return ApiResponse.success(questionFavoriteService.add(questionId == null ? null : String.valueOf(questionId), actor));
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Map<String, Object>> delete(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String questionId) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(questionFavoriteService.delete(questionId, actor));
    }
}
