package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.DiscussionService;
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
@RequestMapping("/api/discussions")
public class DiscussionController {

    private final DiscussionService discussionService;
    private final AuthService authService;

    public DiscussionController(DiscussionService discussionService, AuthService authService) {
        this.discussionService = discussionService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(discussionService.list(category, keyword, pageNo, pageSize, actor));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(discussionService.create(request, actor));
    }

    @GetMapping("/{postId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String postId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(discussionService.detail(postId, actor));
    }

    @PostMapping("/{postId}/replies")
    public ApiResponse<Map<String, Object>> reply(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String postId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(discussionService.reply(postId, request, actor));
    }

    @PostMapping("/{postId}/likes")
    public ApiResponse<Map<String, Object>> like(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String postId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(discussionService.like(postId, actor));
    }

    @DeleteMapping("/{postId}/likes")
    public ApiResponse<Map<String, Object>> unlike(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String postId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER");
        return ApiResponse.success(discussionService.unlike(postId, actor));
    }
}
