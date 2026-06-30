package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.HomeworkService;
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
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final AuthService authService;

    public HomeworkController(HomeworkService homeworkService, AuthService authService) {
        this.homeworkService = homeworkService;
        this.authService = authService;
    }

    @PostMapping("/api/homeworks")
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(homeworkService.create(request, actor));
    }

    @PostMapping("/api/homeworks/{homeworkId}/submits")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String homeworkId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "STUDENT");
        return ApiResponse.success(homeworkService.submit(homeworkId, request, actor));
    }

    @GetMapping("/api/homeworks/{homeworkId}/submits")
    public ApiResponse<Map<String, Object>> listSubmits(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String homeworkId,
            @RequestParam(value = "submit_status", required = false) String submitStatus,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(homeworkService.listSubmits(homeworkId, submitStatus, pageNo, pageSize, actor));
    }

    @PutMapping("/api/homework-reviews/{reviewId}")
    public ApiResponse<Map<String, Object>> review(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String reviewId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER");
        return ApiResponse.success(homeworkService.review(reviewId, request, actor));
    }
}
