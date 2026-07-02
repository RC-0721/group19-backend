package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.TeachingStandardService;
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
@RequestMapping("/api/teaching-standards")
public class TeachingStandardController {

    private final TeachingStandardService teachingStandardService;
    private final AuthService authService;

    public TeachingStandardController(TeachingStandardService teachingStandardService, AuthService authService) {
        this.teachingStandardService = teachingStandardService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "major_id", required = false) String majorId,
            @RequestParam(value = "course_id", required = false) String courseId,
            @RequestParam(value = "standard_type", required = false) String standardType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(teachingStandardService.list(majorId, courseId, standardType, status,
                pageNo, pageSize, actor));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(teachingStandardService.create(request, actor));
    }

    @PutMapping("/{standardId}")
    public ApiResponse<Map<String, Object>> update(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String standardId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(teachingStandardService.update(standardId, request, actor));
    }
}
