package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.TrainingPlanService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;
    private final AuthService authService;

    public TrainingPlanController(TrainingPlanService trainingPlanService, AuthService authService) {
        this.trainingPlanService = trainingPlanService;
        this.authService = authService;
    }

    @GetMapping("/api/training-plans")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "major_id", required = false) String majorId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(trainingPlanService.list(majorId, status, keyword, pageNo, pageSize, actor));
    }

    @PostMapping("/api/training-plans")
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(trainingPlanService.create(request, actor));
    }

    @GetMapping("/api/training-plans/{planId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String planId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(trainingPlanService.detail(planId, actor));
    }

    @GetMapping("/api/training-plans/{planId}/courses")
    public ApiResponse<Map<String, Object>> courses(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String planId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(trainingPlanService.listCourses(planId, actor));
    }

    @PostMapping("/api/training-plans/{planId}/courses")
    public ApiResponse<Map<String, Object>> addCourse(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String planId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(trainingPlanService.addCourse(planId, request, actor));
    }

    @PostMapping("/api/course-prerequisites")
    public ApiResponse<Map<String, Object>> addPrerequisite(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(trainingPlanService.addPrerequisite(request, actor));
    }
}
