package com.group19.teaching.controller;

import com.group19.teaching.common.ApiResponse;
import com.group19.teaching.domain.entity.User;
import com.group19.teaching.service.AuthService;
import com.group19.teaching.service.CourseService;
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
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final AuthService authService;

    public CourseController(CourseService courseService, AuthService authService) {
        this.courseService = courseService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "major_id", required = false) String majorId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("page_no") Integer pageNo,
            @RequestParam("page_size") Integer pageSize) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(courseService.list(keyword, majorId, status, pageNo, pageSize, actor));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestHeader(value = "token", required = false) String token,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(courseService.create(request, actor));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<Map<String, Object>> update(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "EDU_ADMIN");
        return ApiResponse.success(courseService.update(courseId, request, actor));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String courseId,
            @RequestParam("course_class_id") String courseClassId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(courseService.detail(courseId, courseClassId, actor));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<Map<String, Object>> listChapters(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String courseId) {
        User actor = authService.requireRole(token, "STUDENT", "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(courseService.listChapters(courseId, actor));
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<Map<String, Object>> createChapter(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(courseService.createChapter(courseId, request, actor));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<Map<String, Object>> updateChapter(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable String courseId,
            @PathVariable String chapterId,
            @RequestBody Map<String, Object> request) {
        User actor = authService.requireRole(token, "TEACHER", "EDU_ADMIN");
        return ApiResponse.success(courseService.updateChapter(courseId, chapterId, request, actor));
    }
}
